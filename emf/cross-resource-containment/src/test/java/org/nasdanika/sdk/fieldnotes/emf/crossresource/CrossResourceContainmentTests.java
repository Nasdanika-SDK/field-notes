package org.nasdanika.sdk.fieldnotes.emf.crossresource;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.ETypedElement;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.util.InternalEList;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.nasdanika.sdk.fieldnotes.support.TestedWith;

/**
 * The evidence for the note: what containment proxies do when an object is split out into its own
 * resource, saved, loaded, moved back and iterated over, what happens without them, and what a
 * container reference opposite the containment adds.
 * The tests write the files they save to target/evidence, and the note includes them.
 */
class CrossResourceContainmentTests {

	// tag::metamodel[]
	static EPackage tree(boolean resolveProxies) {
		EcoreFactory factory = EcoreFactory.eINSTANCE;
		EPackage tree = factory.createEPackage();
		tree.setName("tree");
		tree.setNsPrefix("tree");
		tree.setNsURI("urn:field-notes:tree");

		EClass node = factory.createEClass();
		node.setName("Node");
		tree.getEClassifiers().add(node);

		EAttribute name = factory.createEAttribute();
		name.setName("name");
		name.setEType(EcorePackage.Literals.ESTRING);
		node.getEStructuralFeatures().add(name);

		EReference children = factory.createEReference();
		children.setName("children");
		children.setEType(node);
		children.setUpperBound(ETypedElement.UNBOUNDED_MULTIPLICITY);
		children.setContainment(true);
		children.setResolveProxies(resolveProxies); // <1>
		node.getEStructuralFeatures().add(children);
		return tree;
	}
	// end::metamodel[]

	// tag::container-metamodel[]
	static EPackage treeWithParent(boolean resolveProxies) {
		EPackage tree = tree(true);
		EClass node = (EClass) tree.getEClassifier("Node");
		EReference children = (EReference) node.getEStructuralFeature("children");

		EReference parent = EcoreFactory.eINSTANCE.createEReference();
		parent.setName("parent");
		parent.setEType(node);
		parent.setEOpposite(children); // <1>
		children.setEOpposite(parent);
		parent.setResolveProxies(resolveProxies); // <2>
		node.getEStructuralFeatures().add(parent);
		return tree;
	}
	// end::container-metamodel[]

	/**
	 * Reflective access to the tree, so that the listings read like generated code.
	 */
	record Tree(EPackage ePackage) {

		EClass nodeClass() {
			return (EClass) ePackage.getEClassifier("Node");
		}

		EObject node(String name) {
			EObject node = EcoreUtil.create(nodeClass());
			node.eSet(nodeClass().getEStructuralFeature("name"), name);
			return node;
		}

		String name(EObject node) {
			return (String) node.eGet(nodeClass().getEStructuralFeature("name"));
		}

		@SuppressWarnings("unchecked")
		EList<EObject> children(EObject node) {
			return (EList<EObject>) node.eGet(nodeClass().getEStructuralFeature("children"));
		}

		EObject parent(EObject node) {
			return (EObject) node.eGet(nodeClass().getEStructuralFeature("parent"));
		}

		EObject basicParent(EObject node) {
			return (EObject) node.eGet(nodeClass().getEStructuralFeature("parent"), false);
		}

		void setParent(EObject node, EObject parent) {
			node.eSet(nodeClass().getEStructuralFeature("parent"), parent);
		}

		ResourceSet resourceSet() {
			ResourceSet resourceSet = new ResourceSetImpl();
			resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
			resourceSet.getPackageRegistry().put(ePackage.getNsURI(), ePackage);
			return resourceSet;
		}

	}

	static Path evidence(String name) throws IOException {
		Path dir = Path.of(System.getProperty("fieldnotes.target", "target"), "evidence", name);
		Files.createDirectories(dir);
		return dir;
	}

	static URI uri(Path dir, String name) {
		return URI.createFileURI(dir.resolve(name).toAbsolutePath().toString());
	}

	static List<EObject> list(Iterator<EObject> iterator) {
		List<EObject> list = new ArrayList<>();
		iterator.forEachRemaining(list::add);
		return list;
	}

	// tag::split[]
	static void split(Tree tree, Path dir) throws IOException {
		ResourceSet resourceSet = tree.resourceSet();
		Resource parentResource = resourceSet.createResource(uri(dir, "parent.xmi"));
		Resource childResource = resourceSet.createResource(uri(dir, "child.xmi"));

		EObject parent = tree.node("parent");
		parentResource.getContents().add(parent);
		EObject child = tree.node("child");
		tree.children(parent).add(child);
		assertThat(child.eResource()).isSameAs(parentResource); // <1>

		childResource.getContents().add(child); // <2>
		assertThat(child.eContainer()).isSameAs(parent); // <3>
		assertThat(child.eResource()).isSameAs(childResource);

		parentResource.save(null);
		childResource.save(null);
		assertThat(Files.readString(dir.resolve("parent.xmi"))).contains("href=\"child.xmi#/\""); // <4>
	}
	// end::split[]

	@Test
	void splitOut() throws IOException {
		split(new Tree(tree(true)), evidence("split-out"));
	}

	@Test
	void loadLazily() throws IOException {
		Tree tree = new Tree(tree(true));
		Path dir = evidence("load");
		split(tree, dir);

		// tag::load[]
		ResourceSet resourceSet = tree.resourceSet();
		Resource parentResource = resourceSet.getResource(uri(dir, "parent.xmi"), true);
		EObject parent = parentResource.getContents().get(0);

		InternalEList<EObject> children = (InternalEList<EObject>) tree.children(parent);
		assertThat(children.basicGet(0).eIsProxy()).isTrue(); // <1>
		assertThat(resourceSet.getResources()).hasSize(1);

		EObject child = children.get(0); // <2>
		assertThat(child.eIsProxy()).isFalse();
		assertThat(child.eContainer()).isSameAs(parent);
		assertThat(child.eResource().getURI().lastSegment()).isEqualTo("child.xmi"); // <3>
		assertThat(resourceSet.getResources()).hasSize(2);

		parentResource.save(null);
		assertThat(Files.readString(dir.resolve("parent.xmi"))).contains("href=\"child.xmi#/\""); // <4>
		// end::load[]
	}

	@Test
	void moveBack() throws IOException {
		Tree tree = new Tree(tree(true));
		Path dir = evidence("move-back");
		split(tree, dir);

		// tag::move-back[]
		ResourceSet resourceSet = tree.resourceSet();
		Resource parentResource = resourceSet.getResource(uri(dir, "parent.xmi"), true);
		EObject parent = parentResource.getContents().get(0);
		EObject child = tree.children(parent).get(0);
		Resource childResource = child.eResource();

		childResource.getContents().remove(child); // <1>
		assertThat(child.eContainer()).isSameAs(parent);
		assertThat(child.eResource()).isSameAs(parentResource); // <2>
		assertThat(childResource.getContents()).isEmpty(); // <3>

		parentResource.save(null);
		assertThat(Files.readString(dir.resolve("parent.xmi"))).contains("<children name=\"child\"/>"); // <4>
		// end::move-back[]
	}

	@Test
	void iterate() throws IOException {
		Tree tree = new Tree(tree(true));
		Path dir = evidence("iterate");
		split(tree, dir);

		// tag::iterate[]
		ResourceSet resourceSet = tree.resourceSet();
		Resource parentResource = resourceSet.getResource(uri(dir, "parent.xmi"), true);

		List<EObject> proper = list(EcoreUtil.getAllProperContents(parentResource, false)); // <1>
		assertThat(proper).hasSize(2);
		assertThat(tree.name(proper.get(0))).isEqualTo("parent");
		assertThat(proper.get(1).eIsProxy()).isTrue();
		assertThat(resourceSet.getResources()).hasSize(1);

		List<EObject> all = list(parentResource.getAllContents()); // <2>
		assertThat(all).extracting(tree::name).containsExactly("parent", "child");
		assertThat(all.get(1).eIsProxy()).isFalse();
		assertThat(resourceSet.getResources()).hasSize(2);
		// end::iterate[]
	}

	@Test
	void childAlone() throws IOException {
		Tree tree = new Tree(tree(true));
		Path dir = evidence("child-alone");
		split(tree, dir);

		// tag::child-alone[]
		ResourceSet resourceSet = tree.resourceSet();
		Resource childResource = resourceSet.getResource(uri(dir, "child.xmi"), true);
		EObject child = childResource.getContents().get(0);
		assertThat(child.eContainer()).isNull();
		assertThat(resourceSet.getResources()).hasSize(1);
		// end::child-alone[]
	}

	@Test
	void withoutContainmentProxies() throws IOException {
		Tree tree = new Tree(tree(false));
		Path dir = evidence("without");

		// tag::without[]
		ResourceSet resourceSet = tree.resourceSet();
		Resource parentResource = resourceSet.createResource(uri(dir, "parent.xmi"));
		Resource childResource = resourceSet.createResource(uri(dir, "child.xmi"));

		EObject parent = tree.node("parent");
		parentResource.getContents().add(parent);
		EObject child = tree.node("child");
		tree.children(parent).add(child);

		childResource.getContents().add(child); // <1>
		assertThat(child.eContainer()).isNull();
		assertThat(tree.children(parent)).isEmpty();
		assertThat(child.eResource()).isSameAs(childResource);

		tree.children(parent).add(child); // <2>
		assertThat(childResource.getContents()).isEmpty();
		assertThat(child.eResource()).isSameAs(parentResource);

		parentResource.save(null);
		assertThat(Files.readString(dir.resolve("parent.xmi"))).contains("<children name=\"child\"/>"); // <3>
		// end::without[]
	}

	@Test
	void containerReference() throws IOException {
		Tree tree = new Tree(treeWithParent(true));
		Path dir = evidence("container");

		// tag::container[]
		ResourceSet resourceSet = tree.resourceSet();
		Resource parentResource = resourceSet.createResource(uri(dir, "parent.xmi"));
		EObject parent = tree.node("parent");
		parentResource.getContents().add(parent);

		EObject child = tree.node("child");
		tree.setParent(child, parent); // <1>
		assertThat(tree.children(parent)).containsExactly(child);
		assertThat(child.eContainer()).isSameAs(parent);

		parentResource.save(null);
		assertThat(Files.readString(dir.resolve("parent.xmi"))).contains("<children name=\"child\"/>"); // <2>

		Resource childResource = resourceSet.createResource(uri(dir, "child.xmi"));
		childResource.getContents().add(child);
		parentResource.save(null);
		childResource.save(null);
		assertThat(Files.readString(dir.resolve("child.xmi"))).contains("<parent href=\"parent.xmi#/\"/>"); // <3>
		// end::container[]
	}

	@Test
	void containerAlone() throws IOException {
		Tree tree = new Tree(treeWithParent(true));
		Path dir = evidence("container-alone");
		split(tree, dir);

		// tag::container-alone[]
		ResourceSet resourceSet = tree.resourceSet();
		Resource childResource = resourceSet.getResource(uri(dir, "child.xmi"), true);
		EObject child = childResource.getContents().get(0);
		assertThat(tree.basicParent(child).eIsProxy()).isTrue(); // <1>
		assertThat(resourceSet.getResources()).hasSize(1);

		EObject parent = tree.parent(child); // <2>
		assertThat(parent.eIsProxy()).isFalse();
		assertThat(tree.name(parent)).isEqualTo("parent");
		assertThat(child.eContainer()).isSameAs(parent);
		assertThat(resourceSet.getResources()).hasSize(2);

		assertThat(tree.children(parent)).containsExactly(child); // <3>
		assertThat(child.eResource()).isSameAs(childResource);
		// end::container-alone[]
	}

	@Test
	void containerNotResolving() throws IOException {
		Tree tree = new Tree(treeWithParent(false));
		Path dir = evidence("container-local");
		split(tree, dir);

		// tag::container-local[]
		ResourceSet resourceSet = tree.resourceSet();
		EObject child = resourceSet.getResource(uri(dir, "child.xmi"), true).getContents().get(0);
		assertThat(tree.parent(child).eIsProxy()).isTrue(); // <1>
		assertThat(resourceSet.getResources()).hasSize(1);

		EObject container = child.eContainer(); // <2>
		assertThat(container.eIsProxy()).isFalse();
		assertThat(resourceSet.getResources()).hasSize(2);
		assertThat(tree.parent(child)).isSameAs(container);
		// end::container-local[]
	}

	@AfterAll
	static void testedWith() throws IOException {
		TestedWith.write(EObject.class, XMIResourceFactoryImpl.class, URI.class);
	}

}
