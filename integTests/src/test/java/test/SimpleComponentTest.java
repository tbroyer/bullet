package test;

import static com.google.common.truth.Truth.*;
import static org.mockito.Mockito.*;

import javax.inject.Inject;

import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.stubbing.defaultanswers.ForwardsInvocations;

import bullet.ObjectGraph;
import dagger.Component;

public class SimpleComponentTest {

  static class A {
    @Inject A() {}
  }
  static class B {
    @Inject A a;
    @Inject B() {}
  }
  static class NotInComponent extends A {
    @Inject NotInComponent() {}
  }

  // XXX: interface must be public for Mockito ForwardsInvocations to work
  @Component
  public interface SimpleComponent {
    A a();
    B b();
  }

  SimpleComponent component;
  ObjectGraph graph;

  @Before public void setUp() {
    // We cannot spy the Dagger‡ component as it's final, so we wrap it in a mock that delegates to it.
    // We want to test both that the method is called (mockito) and that everything actually works (dagger).
    SimpleComponent realComponent = DaggerSimpleComponentTest_SimpleComponent.create();
    this.component = mock(SimpleComponent.class, new ForwardsInvocations(realComponent));
    graph = new BulletSimpleComponentTest_SimpleComponent(component);
  }

  @Test public void testSimpleComponent() {
    A a = graph.get(A.class);
    verify(component).a();
    assertThat(a).isNotNull();
  }

  @Test(expected = IllegalArgumentException.class)
  public void throwsOnUnknownType() {
    graph.get(NotInComponent.class);
  }

  @Test(expected = IllegalArgumentException.class)
  public void throwsOnMembersInjection() {
    B b = new B();
    graph.inject(b);
  }
}
