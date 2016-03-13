package bullet.impl;

import java.util.List;
import java.util.Objects;

import javax.inject.Provider;
import javax.inject.Qualifier;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;

import dagger.Lazy;
import dagger.MembersInjector;
import dagger.Subcomponent;

@AutoValue
abstract class ComponentMethodDescriptor {
  enum ComponentMethodKind {
    SIMPLE_PROVISION,
    PROVIDER_OR_LAZY,
    SIMPLE_MEMBERS_INJECTION,
    MEMBERS_INJECTOR,
  }

  abstract ComponentMethodKind kind();
  abstract TypeMirror type();
  abstract String name();

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != this.getClass()) {
      return false;
    }
    ComponentMethodDescriptor other = (ComponentMethodDescriptor) obj;
    return Objects.equals(this.kind(), other.kind())
        && MoreTypes.equivalence().equivalent(this.type(), other.type())
        && Objects.equals(this.name(), other.name());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        this.kind(),
        MoreTypes.equivalence().wrap(this.type()),
        this.name());
  }

  @Override
  public String toString() {
    switch (this.kind()) {
      case SIMPLE_PROVISION:
        return MethodSpec.methodBuilder(this.name())
            .returns(TypeName.get(this.type()))
            .build()
            .toString();
      case PROVIDER_OR_LAZY:
        return MethodSpec.methodBuilder(this.name())
            .returns(ParameterizedTypeName.get(ClassName.get(Provider.class), TypeName.get(this.type())))
            .build()
            .toString();
      case SIMPLE_MEMBERS_INJECTION:
        return MethodSpec.methodBuilder(this.name())
            .addParameter(TypeName.get(this.type()), "instance")
            .build()
            .toString();
      case MEMBERS_INJECTOR:
        return MethodSpec.methodBuilder(this.name())
            .returns(ParameterizedTypeName.get(ClassName.get(MembersInjector.class), TypeName.get(this.type())))
            .build()
            .toString();
      default:
        return super.toString();
    }
  }

  static Optional<ComponentMethodDescriptor> forComponentMethod(Types types, DeclaredType componentElement, ExecutableElement componentMethod) {
    // Using same algorithm as Dagger's ComponentDescriptor#getDescriptorForComponentMethod
    ExecutableType resolvedComponentMethod = MoreTypes.asExecutable(types.asMemberOf(componentElement, componentMethod));
    TypeMirror returnType = resolvedComponentMethod.getReturnType();
    if (returnType.getKind() == TypeKind.DECLARED) {
      if (MoreTypes.isTypeOf(Provider.class, returnType)
          || MoreTypes.isTypeOf(Lazy.class, returnType)) {
        return methodDescriptor(
            ComponentMethodKind.PROVIDER_OR_LAZY,
            MoreTypes.asDeclared(returnType).getTypeArguments().get(0),
            componentMethod);
      } else if (MoreTypes.isTypeOf(MembersInjector.class, returnType)) {
        return methodDescriptor(
            ComponentMethodKind.MEMBERS_INJECTOR,
            MoreTypes.asDeclared(returnType).getTypeArguments().get(0),
            componentMethod);
      } else if (MoreElements.getAnnotationMirror(types.asElement(returnType), Subcomponent.class).isPresent()) {
        // Ignore subcomponent methods
        return Optional.absent();
      }
    }

    if (resolvedComponentMethod.getParameterTypes().isEmpty()
        && resolvedComponentMethod.getReturnType().getKind() == TypeKind.DECLARED) {
      return methodDescriptor(
          ComponentMethodKind.SIMPLE_PROVISION,
          returnType,
          componentMethod);
    }

    List<? extends TypeMirror> parameterTypes = resolvedComponentMethod.getParameterTypes();
    if (parameterTypes.size() == 1
        && parameterTypes.get(0).getKind() == TypeKind.DECLARED
        && (returnType.getKind().equals(TypeKind.VOID)
            || types.isSameType(returnType, parameterTypes.get(0)))) {
      return methodDescriptor(
          ComponentMethodKind.SIMPLE_MEMBERS_INJECTION,
          parameterTypes.get(0),
          componentMethod);
    }

    // Let Dagger do the validation
    return Optional.absent();
  }

  private static Optional<ComponentMethodDescriptor> methodDescriptor(
      ComponentMethodKind kind, TypeMirror type, ExecutableElement componentMethod) {
    // ObjectGraph API doesn't allow passing qualifier as input, so ignore those methods.
    if (hasQualifier(componentMethod)) {
      return Optional.absent();
    }
    return Optional.<ComponentMethodDescriptor>of(new AutoValue_ComponentMethodDescriptor(kind, type, componentMethod.getSimpleName().toString()));
  }

  static boolean hasQualifier(Element e) {
    return !AnnotationMirrors.getAnnotatedAnnotations(e, Qualifier.class).isEmpty();
  }
}
