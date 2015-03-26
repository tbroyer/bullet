Bullet
======

Provides Dagger†-like ObjectGraph API around Dagger‡ Components

Download
--------

Snapshots of the development version are available in [Sonatype's `snapshots` repository][snap].

 [snap]: https://oss.sonatype.org/content/repositories/snapshots/

Usage
-----

Bullet• generates a class with a `Bullet_` prefix next to your components
(and next to the `Dagger_`-prefixed class that Dagger‡ generates),
whose constructor expects an instance of your component as argument,
and which implements the `bullet.ObjectGraph` interface providing:

 * a `<T> T get(Class<T> type)` method to get an instance of some type `T`
 * a `<T> T inject(T instance)` method to inject members of some existing instance of type `T`

The `ObjectGraph` will delegate to the appropriate method of the wrapped component depending on the argument
and, similarly to Dagger†, throw an `IllegalArgumentException` if none can be found.

Bullet• is triggered by Dagger‡'s `@Component` annotation,
so you only need to put Bullet• in your processor path to get it to work;
no need to change anything to your component interfaces.

License
-------

    Copyright 2014 Thomas Broyer

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.


