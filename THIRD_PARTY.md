# Third-party software

The MIT license covers Kinform project code, not external components or provider services. Dependencies keep their own licenses. Review their notices when redistributing binaries.

| Component | Use | License / source |
| --- | --- | --- |
| Gradle 9.1.0 wrapper | Build bootstrap | [Apache-2.0 and bundled notices](https://github.com/gradle/gradle/blob/v9.1.0/LICENSE) |
| Android Gradle Plugin 9.0.1 | Build | [Android tools / Apache-2.0](https://android.googlesource.com/platform/tools/base/) |
| OkHttp 4.12.0 | Android networking | [Apache-2.0](https://github.com/square/okhttp/blob/parent-4.12.0/LICENSE.txt) |
| Okio, Kotlin standard library | Transitive networking dependencies | Apache-2.0; see resolved artifacts |
| org.json 20240303 | JVM checks only | [Public domain](https://github.com/stleary/JSON-java/blob/20240303/LICENSE) |
| ws 8.22.0 | Optional Node backend | [MIT](https://github.com/websockets/ws/blob/master/LICENSE) |
| selfsigned 5.5.0 | Optional local TLS setup | [MIT](https://github.com/jfromaniello/selfsigned/blob/master/LICENSE) |

The backend lockfile pins transitive dependencies. Android SDK tools are downloaded separately under their own terms. OpenAI API access, models, and voices are external services, not granted by the Kinform source license. Use voices only with appropriate authorization.
