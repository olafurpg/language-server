---
id: sublime
title: Sublime Text
---

Metals has experimental support for Sublime Text.

```sh
coursier bootstrap \
  --java-opt -XX:+UseG1GC \
  --java-opt -XX:+UseStringDeduplication  \
  --java-opt -Xss4m \
  --java-opt -Xms1G \
  --java-opt -Xmx4G  \
  --java-opt -Dmetals.http=true \
  --java-opt -Dmetals.icons=unicode \
  --java-opt -Dmetals.file-watcher=auto \
  org.scalameta:metals_2.12:SNAPSHOT \
  -o /usr/local/bin/metals-sublime -f
```
