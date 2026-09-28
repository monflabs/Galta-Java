# Resource Manifest  


Could use this as well:

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <executions>
        <execution>
            <phase>generate-resources</phase>
            <goals>
                <goal>exec</goal>
            </goals>
            <configuration>
                <executable>sh</executable>
                <arguments>
                    <argument>-c</argument>
                    <argument>
                        cd src/main/resources/config &amp;&amp;
                        find . -type f ! -name resource.manifest | 
                        sed 's|^./||' > resource.manifest
                    </argument>
                </arguments>
            </configuration>
        </execution>
    </executions>
</plugin>
```
