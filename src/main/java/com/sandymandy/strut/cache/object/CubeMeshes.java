package com.sandymandy.strut.cache.object;

import com.sandymandy.strut.util.RenderUtil;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the cubes of a bone into meshes, so what moves meshes (cloth, flesh) can move them too
 * <p>
 * A cube has eight corners, which is too few to bend: each face is cut into a grid of quads, about {@link #CELL} model units across, and the grid's corners are
 * what the soft body moves. The geometry, the texture coordinates and the shading are those of the cube, so until something moves it nothing is different
 * to look at
 */
public final class CubeMeshes {
	/** About how wide one quad of a cut face is, in model units (a sixteenth of a block) */
	private static final float CELL = 1.25f;

	/** The most quads along one edge of a face: a large cube is not worth more */
	private static final int MOST = 6;

	/** Told, with the model, when cubes of it were replaced: what was made from its cubes (the GPU's copy) is out of date */
	public static volatile java.util.function.Consumer<BakedStrutModel> onConverted;

	private CubeMeshes() {}

	/** {@link #convertUnder(GroupBoneDefinition)} for a model's bone, telling {@link #onConverted} if anything was replaced */
	public static boolean convertUnder(BakedStrutModel model, GroupBoneDefinition bone) {
		boolean any = convertUnder(bone);
		java.util.function.Consumer<BakedStrutModel> listener = onConverted;

		if (any && listener != null)
			listener.accept(model);

		return any;
	}

	/**
	 * Replaces the cubes of {@code bone} and everything under it with meshes
	 *
	 * @return Whether there was anything to replace
	 */
	public static boolean convertUnder(GroupBoneDefinition bone) {
		boolean any = convert(bone);

		for (GroupBoneDefinition child : bone.getChildBones())
			any |= convertUnder(child);

		return any;
	}

	/**
	 * Replaces the cubes of {@code bone} alone with one mesh holding all of them (each cube's own turn put into its corners): one mesh is what the GPU and the
	 * cage work with, where a mesh for each of a hundred planes would be a hundred records for each instance. The mesh is added first, so what reads the bone
	 * meanwhile always finds the geometry
	 */
	public static boolean convert(GroupBoneDefinition bone) {
		List<StrutCube> cubes = bone.getCubes();

		if (cubes.isEmpty())
			return false;

		List<StrutPoly> polys = new ArrayList<>();

		for (StrutCube cube : new ArrayList<>(cubes)) {
			StrutMesh mesh = mesh(cube);
			Matrix4f matrix = new Matrix4f();
			Matrix3f normals = new Matrix3f();

			RenderUtil.applyPivotRotation(matrix, normals, mesh.pivot(), mesh.rotation());

			for (StrutPoly poly : mesh.polys())
				polys.add(transformed(poly, matrix, normals));
		}

		if (polys.isEmpty())
			return false;

		bone.getMeshes().add(new StrutMesh(polys.toArray(new StrutPoly[0]), Vec3d.ZERO, Vec3d.ZERO, false));
		bone.getCubes().clear();

		return true;
	}

	private static StrutPoly transformed(StrutPoly poly, Matrix4f matrix, Matrix3f normals) {
		StrutVertex[] corners = new StrutVertex[poly.vertices().length];

		for (int i = 0; i < corners.length; i++) {
			StrutVertex v = poly.vertices()[i];

			corners[i] = new StrutVertex(matrix.transformPosition(new Vector3f(v.position())), v.texU(), v.texV(), normals.transform(new Vector3f(v.normal())));
		}

		return new StrutPoly(corners, normals.transform(new Vector3f(poly.normal())));
	}

	/**
	 * One cube as a mesh: each face cut into a grid, wound as the face is, and carrying the face's normal. A flat cube (a plane, two faces on one plane that the
	 * shader lights by whichever side the eye is on) keeps its two faces, with the doubled normal that tells the shader so, and loses its four edges, which have
	 * no area
	 */
	public static StrutMesh mesh(StrutCube cube) {
		List<StrutPoly> polys = new ArrayList<>();
		boolean flat = cube.size().x == 0 || cube.size().y == 0 || cube.size().z == 0;

		for (StrutQuad quad : cube.quads()) {
			if (quad == null)
				continue;

			StrutVertex[] c = quad.vertices();

			if (flat && area(c) < 1e-12f)
				continue;

			Vector3f normal = new Vector3f(quad.normal());

			RenderUtil.fixInvertedFlatCube(cube, normal);

			Vector3f vertexNormal = flat ? new Vector3f(normal).mul(FLAT_FACE_NORMAL_LENGTH) : normal;
			int across = cuts(c[0].position(), c[1].position());
			int down = cuts(c[0].position(), c[3].position());

			for (int j = 0; j < down; j++) {
				for (int i = 0; i < across; i++) {
					StrutVertex[] corners = {
							at(c, (float)i / across, (float)j / down, vertexNormal),
							at(c, (float)(i + 1) / across, (float)j / down, vertexNormal),
							at(c, (float)(i + 1) / across, (float)(j + 1) / down, vertexNormal),
							at(c, (float)i / across, (float)(j + 1) / down, vertexNormal)};

					polys.add(new StrutPoly(corners, new Vector3f(normal)));
				}
			}
		}

		return new StrutMesh(polys.toArray(new StrutPoly[0]), cube.pivot(), cube.rotation(), false);
	}

	/** How long the normal of a flat face is, so that the shader lights it by the side the eye is on (the same as the cube's own, in the GPU model) */
	private static final float FLAT_FACE_NORMAL_LENGTH = 2f;

	/** Twice the area of the quad, about: the cross of its two diagonals */
	private static float area(StrutVertex[] c) {
		Vector3f a = new Vector3f(c[2].position()).sub(c[0].position());
		Vector3f b = new Vector3f(c[3].position()).sub(c[1].position());

		return a.cross(b).lengthSquared();
	}

	private static int cuts(Vector3f a, Vector3f b) {
		// the positions are in blocks: a sixteenth of one is a model unit
		float length = a.distance(b) * 16f;

		return Math.max(1, Math.min(MOST, Math.round(length / CELL)));
	}

	/** The point of the quad {@code s} of the way from corner 0 to corner 1 and {@code t} of the way from corner 0 to corner 3, with the texture coordinates there */
	private static StrutVertex at(StrutVertex[] c, float s, float t, Vector3f normal) {
		float w0 = (1 - s) * (1 - t);
		float w1 = s * (1 - t);
		float w2 = s * t;
		float w3 = (1 - s) * t;
		Vector3f position = new Vector3f()
				.fma(w0, c[0].position()).fma(w1, c[1].position()).fma(w2, c[2].position()).fma(w3, c[3].position());
		float u = w0 * c[0].texU() + w1 * c[1].texU() + w2 * c[2].texU() + w3 * c[3].texU();
		float v = w0 * c[0].texV() + w1 * c[1].texV() + w2 * c[2].texV() + w3 * c[3].texV();

		return new StrutVertex(position, u, v, new Vector3f(normal));
	}
}
