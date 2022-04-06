## 字节码解释器

- 用 Java 实现的 Java 字节码解释器
- 根据 JVM 规范实现除 invokedynamic 外其他字节码语义的解释执行
- 根据论文 https://dl.acm.org/doi/pdf/10.1145/583810.583821 实现了 Fast Subtype Checking
- 实现了内联缓存