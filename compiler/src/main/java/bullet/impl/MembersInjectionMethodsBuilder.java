package bullet.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;

import javax.lang.model.util.Types;

import com.google.common.collect.Sets;

class MembersInjectionMethodsBuilder {
  private final Types typeUtils;
  final List<Node> allNodes = new ArrayList<>();

  public MembersInjectionMethodsBuilder(Types typeUtils) {
    this.typeUtils = typeUtils;
  }

  public List<ComponentMethodDescriptor> build() {
    if (allNodes.isEmpty()) {
      return Collections.emptyList();
    }

    // first find all nodes with no subtypes
    List<Node> rootNodes = new ArrayList<>(allNodes.size());
    for (Node node : allNodes) {
      if (!node.hasSubtype) {
        rootNodes.add(node);
      }
    }
    assert !rootNodes.isEmpty();
    // then walk the tree from the root nodes
    LinkedHashSet<Node> nodes = Sets.newLinkedHashSetWithExpectedSize(allNodes.size());
    recursiveAdd(nodes, rootNodes);
    // then walk the set and extract the ComponentMethodDescriptors
    List<ComponentMethodDescriptor> methods = new ArrayList<>(allNodes.size());
    for (Node node : nodes) {
      methods.add(node.method);
    }
    return methods;
  }

  private void recursiveAdd(LinkedHashSet<Node> dest, List<Node> src) {
    for (Node node : src) {
      // We want to re-insert at the end, contrary to LinkedHashSet's behavior
      dest.remove(node);
      dest.add(node);
      recursiveAdd(dest, node.supertypes);
    }
  }

  public void add(ComponentMethodDescriptor methodDescriptor) {
    final List<Node> pendingSupertypes = new ArrayList<>(allNodes.size());
    Node newNode = null;
    for (Node node : allNodes) {
      if (typeUtils.isSameType(methodDescriptor.type(), node.method.type())) {
        assert newNode == null;
        return;
      }
      if (typeUtils.isSubtype(methodDescriptor.type(), node.method.type())) {
        addSupertype(pendingSupertypes, node);
        continue;
      }
      if (typeUtils.isSubtype(node.method.type(), methodDescriptor.type())) {
        newNode = addSubtype(node, methodDescriptor, newNode);
      }
    }
    if (newNode == null) {
      // methodDescriptor's type is not a supertype of any known type
      newNode = new Node(methodDescriptor);
    }
    allNodes.add(newNode);
    newNode.supertypes.addAll(pendingSupertypes);
    for (Node supertype : newNode.supertypes) {
      supertype.hasSubtype = true;
    }
  }

  /**
   * Insert {@code node} into {@code supertypes}, removing values from
   * {@code supertypes} that are themselves supertypes of {@code node}.
   */
  private void addSupertype(List<Node> supertypes, Node node) {
    final ListIterator<Node> it = supertypes.listIterator();
    boolean added = false;
    while (it.hasNext()) {
      Node supertype = it.next();
      if (typeUtils.isSameType(node.method.type(), supertype.method.type())) {
        assert !added;
        return;
      }
      if (typeUtils.isSubtype(node.method.type(), supertype.method.type())) {
        if (added) {
          it.remove();
        } else {
          it.set(node);
          added = true;
        }
        continue;
      }
      if (typeUtils.isSubtype(supertype.method.type(), node.method.type())) {
        assert !added;
        return;
      }
    }
    if (!added) {
      supertypes.add(node);
    }
  }

  /**
   * Add {@code node} as a subtype of {@code methodDescriptor}.
   *
   * <p>If {@code methodDescriptor}'s {@link ComponentMethodDescriptor#type() type} is already
   * a supertype of {@code node}, or a supertype of a listed supertype, then this is a no-op.
   * <p>Otherwise, a new {@link Node} is inserted as a supertype of {@code node} <i>between</i>
   * all {@code node}'s supertypes that are themselves supertypes of {@code methodDescriptor}'s
   * {@link ComponentMethodDescriptor#type() type}.
   *
   * <p>The <i>new {@link Node}</i> might actually have already been created by a previous call
   * to this method; it's then passed as the {@code newNode} argument, and then <b>always</b>
   * returned, whether the tree has been modified or not.
   *
   * @return the inserted {@link Node} if any, {@code null} otherwise.
   */
  private Node addSubtype(Node node, ComponentMethodDescriptor methodDescriptor, Node newNode) {
    final List<Node> pendingSupertypes = new ArrayList<>();
    for (Node supertype : node.supertypes) {
      if (typeUtils.isSameType(methodDescriptor.type(), supertype.method.type())) {
        return newNode;
      }
      if (typeUtils.isSubtype(methodDescriptor.type(), supertype.method.type())) {
        pendingSupertypes.add(supertype);
      }
      if (typeUtils.isSubtype(supertype.method.type(), methodDescriptor.type())) {
        // 'methodDescriptor' should be added as a supertype of 'supertype' instead.
        return newNode;
      }
    }
    if (newNode == null) {
      newNode = new Node(methodDescriptor);
    }
    node.supertypes.removeAll(pendingSupertypes);
    node.supertypes.add(newNode);
    for (Node supertype : pendingSupertypes) {
      addSupertype(newNode.supertypes, supertype);
      supertype.hasSubtype = true;
    }
    return newNode;
  }

  private static class Node {
    final ComponentMethodDescriptor method;
    final List<Node> supertypes = new ArrayList<>();
    boolean hasSubtype;

    Node(ComponentMethodDescriptor method) {
      this.method = method;
    }

    @Override
    public String toString() {
      return method.toString();
    }
  }
}
