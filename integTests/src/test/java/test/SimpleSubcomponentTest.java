package test;

import static com.google.common.truth.Truth.*;
import static org.mockito.Mockito.*;

import javax.inject.Inject;

import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.stubbing.defaultanswers.ForwardsInvocations;

import bullet.ObjectGraph;
import dagger.Component;
import dagger.Subcomponent;

public class SimpleSubcomponentTest {

  static class A {
    @Inject A() {}
  }
  static class B {
    @Inject A a;
    @Inject B() {}
  }
  static class C {
    @Inject B b;
    @Inject C() {}
  }
  static class NotInComponent extends A {
    @Inject NotInComponent() {}
  }

  @Component
  interface SimpleComponent {
    SimpleSubcomponent subcomponent();
  }
  @Subcomponent
  interface OtherSubcomponent {
    C c();
  }

  // XXX: interface must be public for Mockito ForwardsInvocations to work
  @Subcomponent
  public interface SimpleSubcomponent {
    A a();
    B b();
    OtherSubcomponent subcomponent();
  }

  SimpleSubcomponent component;
  ObjectGraph graph;

  @Before public void setUp() {
    // We cannot spy the Dagger‡ component as it's final, so we wrap it in a mock that delegates to it.
    // We want to test both that the method is called (mockito) and that everything actually works (dagger).
    SimpleSubcomponent realComponent = DaggerSimpleSubcomponentTest_SimpleComponent.create().subcomponent();
    this.component = mock(SimpleSubcomponent.class, new ForwardsInvocations(realComponent));
    graph = new BulletSimpleSubcomponentTest_SimpleSubcomponent(component);
  }

  @Test public void testSimpleSubcomponent() {
    A a = graph.get(A.class);
    verify(component).a();
    assertThat(a).isNotNull();
  }

  @Test(expected = IllegalArgumentException.class)
  public void throwsOnUnknownType() {
    graph.get(NotInComponent.class);
  }

  @Test(expected = IllegalArgumentException.class)
  public void throwsOnSubcomponent() {
    graph.get(OtherSubcomponent.class);
  }

  @Test(expected = IllegalArgumentException.class)
  public void throwsOnMembersInjection() {
    B b = new B();
    graph.inject(b);
  }
}
