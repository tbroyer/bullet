Bullet
======

Provides Dagger†-like ObjectGraph API around Dagger‡ Components

Download
--------

Releases are deployed to [the Central Repository][releases]

Snapshots of the development version are available in [Sonatype's `snapshots` repository][snap].

 [releases]: https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22net.ltgt.dagger%22
 [snap]: https://oss.sonatype.org/content/repositories/snapshots/

When to use Bullet•
-------------------

If you don't have a use-case for Bullet•, don't use it.
If you do have a use-case for it, try to refactor your code to remove that need.
Bullet• can possibly be useful when migrating from Dagger† to Dagger‡, but not everyone agrees.

Usage
-----

Bullet• generates a class with a `Bullet` prefix next to your components
(and next to the `Dagger_`-prefixed class that Dagger‡ generates),
whose constructor expects an instance of your component as argument,
and which implements the `bullet.ObjectGraph` interface providing:

 * a `<T> T get(Class<T> type)` method to get an instance of some type `T`
 * a `<T> T inject(T instance)` method to inject members of some existing instance of type `T`

The `ObjectGraph` will delegate to the appropriate method of the wrapped component depending on the argument
and, similarly to Dagger†, throw an `IllegalArgumentException` if none can be found.

Bullet• is triggered by Dagger‡'s `@Component` and `@Subcomponent` annotations,
so you only need to put Bullet• in your processor path to get it to work;
no need to change anything to your component interfaces.

Notes on name and version
-------------------------

Dagger's name comes from a play on words because it builds a directed acyclic graph (DAG).
It happens that Unicode has a character named dagger (†): codepoint U+2020.
Dagger 2 is sometimes shortened to ‡, the double-dagger Unicode character, codepoint U+2021.
Bullet is thus named after Unicode's U+2022.

Because I started this project only as a proof-of-concept and mostly _for fun_, and to learn more about annotation processors,
I'll name versions after firearms calibers (not that I like guns or find them “fun” –I don't– but just to continue on the pun),
possibly ending with a 1.0 _silver bullet_.

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


