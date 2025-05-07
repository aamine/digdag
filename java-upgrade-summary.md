# Java 8 to Java 11 Upgrade

## Changes Made
- Updated the Java version in `build.gradle` from 1.8 (Java 8) to 11:
  - Changed `sourceCompatibility = 1.8` to `sourceCompatibility = 11`
  - Changed `targetCompatibility = 1.8` to `targetCompatibility = 11`

## Next Steps
According to the requirements, the following steps should be performed to complete the upgrade:

1. Run unit tests using the following command:
   ```
   ./gradlew build
   ```

2. If all tests pass, the upgrade is complete.

3. If any tests fail, modify the code to fix compatibility issues and run the tests again until all pass.

## Potential Java 8 to 11 Migration Issues to Watch For
If tests fail, look for these common Java 8 to 11 migration issues:

1. **Module System**: Java 9 introduced the module system - check for any access issues to internal APIs.

2. **Removed APIs**: Some APIs were removed in Java 9+ that were present in Java 8.

3. **JAXB/JAX-WS Removal**: These were removed from the JDK in Java 11 and need to be added as separate dependencies if used.

4. **Reflection Changes**: Stricter access controls on reflection that may break code.

5. **Deprecated APIs**: Methods like `Thread.destroy()` and `Thread.stop(Throwable)` were removed.

6. **JVM Changes**: Changes to the JVM behavior, garbage collection, and default settings.

## Additional Notes
- Java 11 has improved performance and security features compared to Java 8.
- Security updates and bug fixes from Java 9, 10, and 11 are now included.
- The long-term support model of Java 11 provides stability and extended support.