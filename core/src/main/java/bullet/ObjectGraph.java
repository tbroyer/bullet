/*
 * Copyright (C) 2014 Thomas Broyer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package bullet;

public interface ObjectGraph {
  /**
   * Returns an instance of type.
   *
   * @throws java.lang.IllegalArgumentException if type is not one of this object graph's injectable types.
   */
  <T> T get(Class<T> type);

  /**
   * Injects the members of instance, including injectable members inherited from its supertypes.
   *
   * @throws java.lang.IllegalArgumentException if the runtime type of instance is not one of this object graph's injectable types.
   */
  <T> T inject(T instance);
}
