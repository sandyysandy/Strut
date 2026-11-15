package com.sandymandy.strut.physics.flesh;

import com.sandymandy.strut.animation.state.ModelInstance;
import com.sandymandy.strut.animation.state.Pose;
import com.sandymandy.strut.cache.object.BakedStrutModel;
import com.sandymandy.strut.cache.object.CubeMeshes;
import com.sandymandy.strut.cache.object.GroupBoneDefinition;
import com.sandymandy.strut.cache.object.StrutCube;
import com.sandymandy.strut.cache.object.StrutMesh;
import com.sandymandy.strut.cache.object.StrutPoly;
import com.sandymandy.strut.cache.object.StrutQuad;
import com.sandymandy.strut.cache.object.StrutVertex;
import com.sandymandy.strut.loading.json.raw.ModelProperties;
import com.sandymandy.strut.physics.PhysicsInstance;
import com.sandymandy.strut.physics.collision.Bodies;
import com.sandymandy.strut.physics.collision.Collider;
import com.sandymandy.strut.physics.config.PhysicsConfig;
import com.sandymandy.strut.physics.tools.PhysicsControls;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** A soft body on a bone of cubes: the cubes are cut into meshes that look as they did, and the cage moves them */
class CubeFleshTest {
	/** A cube of {@code size} model units whose near corner is at {@code (x, y, z)} blocks, its faces wound as the baker winds them: round each face */
	private static StrutCube cube(float x, float y, float z, float size) {
		float s = size / 16f;
		Vector3f[][] faces = {
				{p(x, y, z), p(x, y, z + s), p(x, y + s, z + s), p(x, y + s, z)},
				{p(x + s, y, z + s), p(x + s, y, z), p(x + s, y + s, z), p(x + s, y + s, z + s)},
				{p(x + s, y, z), p(x, y, z), p(x, y + s, z), p(x + s, y + s, z)},
				{p(x, y, z + s), p(x + s, y, z + s), p(x + s, y + s, z + s), p(x, y + s, z + s)},
				{p(x, y + s, z), p(x, y + s, z + s), p(x + s, y + s, z + s), p(x + s, y + s, z)},
				{p(x, y, z + s), p(x, y, z), p(x + s, y, z), p(x + s, y, z + s)}};
		Direction[] directions = {Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH, Direction.UP, Direction.DOWN};
		StrutQuad[] quads = new StrutQuad[6];

		for (int f = 0; f < 6; f++) {
			Vector3f normal = new Vector3f(directions[f].getOffsetX(), directions[f].getOffsetY(), directions[f].getOffsetZ());
			StrutVertex[] corners = new StrutVertex[4];
			float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};

			for (int k = 0; k < 4; k++)
				corners[k] = new StrutVertex(faces[f][k], uv[k][0] * 0.25f + f * 0.1f, uv[k][1] * 0.25f, normal);

			quads[f] = new StrutQuad(corners, normal, directions[f]);
		}

		return new StrutCube(quads, Vec3d.ZERO, Vec3d.ZERO, new Vec3d(size, size, size), 0, false);
	}

	/** A plane {@code size} units square lying flat at {@code y}: a cube with no height, wound as the baker winds one, its four edges of no area */
	private static StrutCube plane(float x, float y, float z, float size) {
		float s = size / 16f;
		Vector3f[][] faces = {
				{p(x, y, z), p(x, y, z + s), p(x, y, z + s), p(x, y, z)},
				{p(x + s, y, z + s), p(x + s, y, z), p(x + s, y, z), p(x + s, y, z + s)},
				{p(x + s, y, z), p(x, y, z), p(x, y, z), p(x + s, y, z)},
				{p(x, y, z + s), p(x + s, y, z + s), p(x + s, y, z + s), p(x, y, z + s)},
				{p(x, y, z), p(x, y, z + s), p(x + s, y, z + s), p(x + s, y, z)},
				{p(x, y, z + s), p(x, y, z), p(x + s, y, z), p(x + s, y, z + s)}};
		Direction[] directions = {Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH, Direction.UP, Direction.DOWN};
		StrutQuad[] quads = new StrutQuad[6];

		for (int f = 0; f < 6; f++) {
			Vector3f normal = new Vector3f(directions[f].getOffsetX(), directions[f].getOffsetY(), directions[f].getOffsetZ());
			StrutVertex[] corners = new StrutVertex[4];

			for (int k = 0; k < 4; k++)
				corners[k] = new StrutVertex(faces[f][k], 0.1f * k, 0.1f * f, normal);

			quads[f] = new StrutQuad(corners, normal, directions[f]);
		}

		return new StrutCube(quads, Vec3d.ZERO, Vec3d.ZERO, new Vec3d(size, 0, size), 0, false);
	}

	private static Vector3f p(float x, float y, float z) {
		return new Vector3f(x, y, z);
	}

	private static BakedStrutModel model(StrutCube... cubes) {
		GroupBoneDefinition root = new GroupBoneDefinition(null, "root", false, null, null, null);
		GroupBoneDefinition tuft = new GroupBoneDefinition(root, "tuft", false, null, null, null);

		root.bakePivot(0, 0, 0);
		tuft.bakePivot(0, 16, 0);
		root.getChildBones().add(tuft);
		for (StrutCube cube : cubes)
			tuft.getCubes().add(cube);

		ModelProperties properties = new ModelProperties(null, null, null, null, null, null, null, null, null, null, "test", null, 16, 16, null, new double[] {0, 0, 0}, null);

		return new BakedStrutModel(List.of(root), properties);
	}

	@Test
	void aCubeBecomesAMeshWithTheSameCornersAndTextureCoordinates() {
		StrutCube cube = cube(0, 0.6f, 0.3f, 4f);
		StrutMesh mesh = CubeMeshes.mesh(cube);

		assertTrue(mesh.polys().length > 6, "each face is cut into more than one quad");

		float[] low = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
		float[] high = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};

		for (StrutPoly poly : mesh.polys()) {
			assertEquals(4, poly.vertices().length);

			for (StrutVertex vertex : poly.vertices()) {
				low[0] = Math.min(low[0], vertex.position().x);
				low[1] = Math.min(low[1], vertex.position().y);
				low[2] = Math.min(low[2], vertex.position().z);
				high[0] = Math.max(high[0], vertex.position().x);
				high[1] = Math.max(high[1], vertex.position().y);
				high[2] = Math.max(high[2], vertex.position().z);
			}
		}

		assertEquals(0f, low[0], 1e-6);
		assertEquals(0.6f, low[1], 1e-6);
		assertEquals(0.3f, low[2], 1e-6);
		assertEquals(0.25f, high[0], 1e-6);
		assertEquals(0.85f, high[1], 1e-6);
		assertEquals(0.55f, high[2], 1e-6);

		// the first corner of the first face keeps its texture coordinates
		StrutVertex first = mesh.polys()[0].vertices()[0];

		assertEquals(cube.quads()[0].vertices()[0].texU(), first.texU(), 1e-6);
		assertEquals(cube.quads()[0].vertices()[0].texV(), first.texV(), 1e-6);
	}

	@Test
	void aPlaneKeepsItsTwoFacesWithTheDoubledNormalAndLosesItsEdges() {
		StrutMesh mesh = CubeMeshes.mesh(plane(0, 0.6f, 0, 1f));

		assertEquals(2, mesh.polys().length, "the top and the bottom, a quad each; the four edges have no area");

		for (StrutPoly poly : mesh.polys()) {
			for (StrutVertex vertex : poly.vertices())
				assertEquals(2f, vertex.normal().length(), 1e-6, "the shader lights a plane by the side the eye is on when its normal is twice as long");

			assertEquals(1f, poly.normal().length(), 1e-6);
		}
	}

	@Test
	void convertingMovesTheCubesOfABoneToMeshesOnce() {
		BakedStrutModel model = model(cube(0, 0.6f, 0.3f, 4f), cube(0.1f, 0.6f, 0.3f, 2f));
		GroupBoneDefinition tuft = model.getBone("tuft").orElseThrow();

		assertTrue(CubeMeshes.convertUnder(model, model.getBone("root").orElseThrow()));
		assertTrue(tuft.getCubes().isEmpty());
		assertEquals(1, tuft.getMeshes().size(), "the cubes of a bone are one mesh");
		assertFalse(CubeMeshes.convertUnder(model, model.getBone("root").orElseThrow()), "nothing left to convert");
	}

	@Test
	void aSoftBodyOnACubeBoneIsDentedByWhatPressesIt() {
		BakedStrutModel model = model(cube(-0.05f, 0.62f, 0.25f, 12f));
		ModelInstance modelInstance = new ModelInstance();

		modelInstance.poseFor(model);
		modelInstance.resetPoseHistory();

		Pose animated = modelInstance.renderedPose();
		Pose pose = new Pose(animated.boneCount());

		pose.copyFrom(animated);

		PhysicsInstance instance = new PhysicsInstance(model, PhysicsConfig.parse("{\"flesh\": {\"tuft\": {\"bone\": \"tuft\", \"preset\": \"jelly\"}}}", "test"));

		instance.entityId(5);

		Collider.Placed hand = new Collider.Placed();

		hand.shape = Collider.Shape.SPHERE;
		hand.radius = 0.08;
		hand.p[1] = 64 + 0.8;
		hand.p[2] = 0.3;
		hand.layer = "body";

		for (int i = 0; i < 40; i++) {
			Bodies.publishModel(99, new ArrayList<>(List.of(hand)));
			Bodies.nextFrame();
			pose.copyFrom(animated);
			instance.update(model, animated, pose, new PhysicsInstance.Sample(0, 64, 0, 180f, i / 60.0), new PhysicsControls());
		}

		float[] most = {0};

		instance.clothDeforms((mesh, deform) -> {
			for (float o : deform.cornerOffsets())
				most[0] = Math.max(most[0], Math.abs(o));
		});

		assertTrue(most[0] > 1e-4f, "the cube moved with the cage");
	}

	@Test
	void aMeshCarriedByAPairOfBonesRidesOnOnlyOneOfThem() {
		GroupBoneDefinition root = new GroupBoneDefinition(null, "root", false, null, null, null);
		GroupBoneDefinition left = new GroupBoneDefinition(root, "left", false, null, null, null);
		GroupBoneDefinition right = new GroupBoneDefinition(root, "right", false, null, null, null);
		GroupBoneDefinition cover = new GroupBoneDefinition(root, "cover", false, null, null, null);

		root.bakePivot(0, 0, 0);
		left.bakePivot(0, 16, 0);
		right.bakePivot(0, 16, 0);
		cover.bakePivot(0, 16, 0);
		root.getChildBones().add(left);
		root.getChildBones().add(right);
		root.getChildBones().add(cover);
		left.getCubes().add(cube(-0.3f, 0.6f, 0.25f, 8f));
		right.getCubes().add(cube(0.1f, 0.6f, 0.25f, 8f));
		cover.getCubes().add(cube(-0.3f, 0.75f, 0.25f, 4f));

		ModelProperties properties = new ModelProperties(null, null, null, null, null, null, null, null, null, null, "test", null, 16, 16, null, new double[] {0, 0, 0}, null);
		BakedStrutModel model = new BakedStrutModel(List.of(root), properties);
		PhysicsInstance instance = new PhysicsInstance(model, PhysicsConfig.parse("{\"flesh\": {\"pair\": {\"bones\": [\"left\", \"right\"], \"preset\": \"jelly\", \"carry\": [\"cover\"]}}}", "test"));
		java.util.Set<Object> meshes = new java.util.HashSet<>();
		int[] entries = {0};
		ModelInstance modelInstance = new ModelInstance();

		modelInstance.poseFor(model);
		modelInstance.resetPoseHistory();
		instance.entityId(5);

		Pose animated = modelInstance.renderedPose();
		Pose pose = new Pose(animated.boneCount());

		for (int i = 0; i < 30; i++) {
			Bodies.nextFrame();
			pose.copyFrom(animated);
			instance.update(model, animated, pose, new PhysicsInstance.Sample(i * 0.05, 64, 0, 180f, i / 60.0), new PhysicsControls());
		}

		instance.clothDeforms((mesh, deform) -> {
			meshes.add(mesh);
			entries[0]++;
		});

		assertEquals(3, entries[0], "the two pouches and the cover, each once");
		assertEquals(3, meshes.size());
	}

	/** How many of the meshes the flesh moves are rigid riders, after some frames, for a pouch with a cube of its own and a bone under it that has one too */
	private static int riders(String extra) {
		GroupBoneDefinition root = new GroupBoneDefinition(null, "root", false, null, null, null);
		GroupBoneDefinition pouch = new GroupBoneDefinition(root, "pouch", false, null, null, null);
		GroupBoneDefinition under = new GroupBoneDefinition(pouch, "under", false, null, null, null);

		root.bakePivot(0, 0, 0);
		pouch.bakePivot(0, 16, 0);
		under.bakePivot(0, 16, 0);
		root.getChildBones().add(pouch);
		pouch.getChildBones().add(under);
		pouch.getCubes().add(cube(-0.1f, 0.6f, 0.25f, 8f));
		under.getCubes().add(cube(0.05f, 0.6f, 0.25f, 8f));

		ModelProperties properties = new ModelProperties(null, null, null, null, null, null, null, null, null, null, "test", null, 16, 16, null, new double[] {0, 0, 0}, null);
		BakedStrutModel model = new BakedStrutModel(List.of(root), properties);
		PhysicsInstance instance = new PhysicsInstance(model, PhysicsConfig.parse("{\"flesh\": {\"p\": {\"bone\": \"pouch\", \"preset\": \"jelly\"" + extra + "}}}", "test"));
		ModelInstance modelInstance = new ModelInstance();

		modelInstance.poseFor(model);
		modelInstance.resetPoseHistory();
		instance.entityId(5);

		Pose animated = modelInstance.renderedPose();
		Pose pose = new Pose(animated.boneCount());

		for (int i = 0; i < 30; i++) {
			Bodies.nextFrame();
			pose.copyFrom(animated);
			instance.update(model, animated, pose, new PhysicsInstance.Sample(i * 0.05, 64, 0, 180f, i / 60.0), new PhysicsControls());
		}

		int[] count = {0};

		instance.clothDeforms((mesh, deform) -> {
			if (deform instanceof com.sandymandy.strut.animation.state.RigidDeform)
				count[0]++;
		});

		return count[0];
	}

	@Test
	void meshesOnBonesUnderARootThatHasMeshesRideRigidlyUnlessNestedIsSoft() {
		assertEquals(1, riders(""), "by default a mesh on a bone under the pouch is a rigid rider");
		assertEquals(0, riders(", \"nested\": \"soft\""), "with nested soft it is soft like the pouch's own");
	}
}
