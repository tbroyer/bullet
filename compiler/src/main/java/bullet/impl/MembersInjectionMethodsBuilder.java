package bullet.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

import com.google.auto.common.MoreElements;

class MembersInjectionMethodsBuilder {
  private final Types typeUtils;
  private final List<ComponentMethodDescriptor> methods = new ArrayList<>();

  public MembersInjectionMethodsBuilder(Types typeUtils) {
    this.typeUtils = typeUtils;
  }

  public void add(ComponentMethodDescriptor method) {
    methods.add(method);
  }

  public List<ComponentMethodDescriptor> build() {
    // Order members-injection methods from most-specific to least-specific types, for cascading ifs of instanceof.
    Collections.sort(methods, new Comparator<ComponentMethodDescriptor>() {
      @Override
      public int compare(ComponentMethodDescriptor o1, ComponentMethodDescriptor o2) {
        TypeMirror t1 = o1.type(), t2 = o2.type();
        if (typeUtils.isSameType(t1, t2)) {
          return 0;
        } else if (typeUtils.isSubtype(t1, t2)) {
          return -1;
        } else if (typeUtils.isSubtype(t2, t1)) {
          return 1;
        }
        return getName(t1).compareTo(getName(t2));
      }

      private String getName(TypeMirror type) {
        return MoreElements.asType(typeUtils.asElement(type)).getQualifiedName().toString();
      }
    });
    return methods;
  }
}
