-- Seed 100 Java questions into question_db.q_question
-- Distribution: SINGLE/MULTI/JUDGE/FILL/SHORT = 20 each
-- Safe to rerun because IDs are fixed and inserts are IGNORE mode.

USE question_db;

INSERT IGNORE INTO q_question (
    id, type, stem, difficulty, knowledge_point, analysis, answer, options_json, created_by
)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 20
)
SELECT
    730000100000 + n AS id,
    'SINGLE' AS type,
    CONCAT(
        '[JAVA-SINGLE-', LPAD(n, 2, '0'), '] ',
        CASE ((n - 1) % 5)
            WHEN 0 THEN 'What is the main purpose of serialVersionUID?'
            WHEN 1 THEN 'In JDK8, ArrayList usually grows by what factor when expanded?'
            WHEN 2 THEN 'Which keyword is used to declare a constant variable?'
            WHEN 3 THEN 'Which one is a checked exception?'
            ELSE 'Which code gets the current thread name directly?'
        END
    ) AS stem,
    1 + ((n - 1) % 5) AS difficulty,
    CASE ((n - 1) % 5)
        WHEN 0 THEN 'Serialization'
        WHEN 1 THEN 'Collections'
        WHEN 2 THEN 'Language Basics'
        WHEN 3 THEN 'Exception Handling'
        ELSE 'Concurrency Basics'
    END AS knowledge_point,
    CASE ((n - 1) % 5)
        WHEN 0 THEN 'serialVersionUID controls compatibility for serialized objects.'
        WHEN 1 THEN 'In JDK8, ArrayList expansion is roughly 1.5x.'
        WHEN 2 THEN 'final can be used for constants.'
        WHEN 3 THEN 'IOException is a checked exception.'
        ELSE 'Use Thread.currentThread().getName() for current thread name.'
    END AS analysis,
    CASE ((n - 1) % 5)
        WHEN 0 THEN 'A'
        WHEN 1 THEN 'B'
        WHEN 2 THEN 'C'
        WHEN 3 THEN 'C'
        ELSE 'B'
    END AS answer,
    CASE ((n - 1) % 5)
        WHEN 0 THEN JSON_ARRAY(
            JSON_OBJECT('key', 'A', 'text', 'Control serialization compatibility'),
            JSON_OBJECT('key', 'B', 'text', 'Configure JVM heap size'),
            JSON_OBJECT('key', 'C', 'text', 'Set thread priority'),
            JSON_OBJECT('key', 'D', 'text', 'Tune GC threshold')
        )
        WHEN 1 THEN JSON_ARRAY(
            JSON_OBJECT('key', 'A', 'text', '2.0x'),
            JSON_OBJECT('key', 'B', 'text', '1.5x'),
            JSON_OBJECT('key', 'C', 'text', 'Add 10 items'),
            JSON_OBJECT('key', 'D', 'text', 'No growth')
        )
        WHEN 2 THEN JSON_ARRAY(
            JSON_OBJECT('key', 'A', 'text', 'static'),
            JSON_OBJECT('key', 'B', 'text', 'const'),
            JSON_OBJECT('key', 'C', 'text', 'final'),
            JSON_OBJECT('key', 'D', 'text', 'immutable')
        )
        WHEN 3 THEN JSON_ARRAY(
            JSON_OBJECT('key', 'A', 'text', 'NullPointerException'),
            JSON_OBJECT('key', 'B', 'text', 'IllegalArgumentException'),
            JSON_OBJECT('key', 'C', 'text', 'IOException'),
            JSON_OBJECT('key', 'D', 'text', 'ArithmeticException')
        )
        ELSE JSON_ARRAY(
            JSON_OBJECT('key', 'A', 'text', 'System.threadName()'),
            JSON_OBJECT('key', 'B', 'text', 'Thread.currentThread().getName()'),
            JSON_OBJECT('key', 'C', 'text', 'Runtime.getThreadName()'),
            JSON_OBJECT('key', 'D', 'text', 'this.getThread().name()')
        )
    END AS options_json,
    21001 AS created_by
FROM seq;

INSERT IGNORE INTO q_question (
    id, type, stem, difficulty, knowledge_point, analysis, answer, options_json, created_by
)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 20
)
SELECT
    730000200000 + n AS id,
    'MULTI' AS type,
    CONCAT(
        '[JAVA-MULTI-', LPAD(n, 2, '0'), '] ',
        CASE ((n - 1) % 5)
            WHEN 0 THEN 'Which are Java primitive types?'
            WHEN 1 THEN 'Which collections allow duplicate elements?'
            WHEN 2 THEN 'Which statements about interfaces are correct?'
            WHEN 3 THEN 'Which are valid ways to create thread tasks?'
            ELSE 'Which classes are in java.util.concurrent?'
        END
    ) AS stem,
    1 + ((n - 1) % 5) AS difficulty,
    CASE ((n - 1) % 5)
        WHEN 0 THEN 'Language Basics'
        WHEN 1 THEN 'Collections'
        WHEN 2 THEN 'OOP'
        WHEN 3 THEN 'Concurrency'
        ELSE 'Concurrency Utilities'
    END AS knowledge_point,
    CASE ((n - 1) % 5)
        WHEN 0 THEN 'int, boolean and double are primitives; String is not.'
        WHEN 1 THEN 'ArrayList and LinkedList allow duplicates.'
        WHEN 2 THEN 'Interfaces can define default/static methods but no constructors.'
        WHEN 3 THEN 'Thread, Runnable, Callable+FutureTask are all valid approaches.'
        ELSE 'CountDownLatch, ConcurrentHashMap and CopyOnWriteArrayList are in concurrent package.'
    END AS analysis,
    CASE ((n - 1) % 5)
        WHEN 0 THEN 'A,C,D'
        WHEN 1 THEN 'A,C'
        WHEN 2 THEN 'A,C'
        WHEN 3 THEN 'A,B,C'
        ELSE 'A,B,C'
    END AS answer,
    CASE ((n - 1) % 5)
        WHEN 0 THEN JSON_ARRAY(
            JSON_OBJECT('key', 'A', 'text', 'int'),
            JSON_OBJECT('key', 'B', 'text', 'String'),
            JSON_OBJECT('key', 'C', 'text', 'boolean'),
            JSON_OBJECT('key', 'D', 'text', 'double')
        )
        WHEN 1 THEN JSON_ARRAY(
            JSON_OBJECT('key', 'A', 'text', 'ArrayList'),
            JSON_OBJECT('key', 'B', 'text', 'HashSet'),
            JSON_OBJECT('key', 'C', 'text', 'LinkedList'),
            JSON_OBJECT('key', 'D', 'text', 'TreeSet')
        )
        WHEN 2 THEN JSON_ARRAY(
            JSON_OBJECT('key', 'A', 'text', 'Interfaces can have default methods since Java 8'),
            JSON_OBJECT('key', 'B', 'text', 'All default methods must be overridden'),
            JSON_OBJECT('key', 'C', 'text', 'Interfaces can have static methods'),
            JSON_OBJECT('key', 'D', 'text', 'Interfaces can define constructors')
        )
        WHEN 3 THEN JSON_ARRAY(
            JSON_OBJECT('key', 'A', 'text', 'Extend Thread'),
            JSON_OBJECT('key', 'B', 'text', 'Implement Runnable'),
            JSON_OBJECT('key', 'C', 'text', 'Implement Callable and wrap by FutureTask'),
            JSON_OBJECT('key', 'D', 'text', 'Override Object.wait')
        )
        ELSE JSON_ARRAY(
            JSON_OBJECT('key', 'A', 'text', 'CountDownLatch'),
            JSON_OBJECT('key', 'B', 'text', 'ConcurrentHashMap'),
            JSON_OBJECT('key', 'C', 'text', 'CopyOnWriteArrayList'),
            JSON_OBJECT('key', 'D', 'text', 'Optional')
        )
    END AS options_json,
    21001 AS created_by
FROM seq;

INSERT IGNORE INTO q_question (
    id, type, stem, difficulty, knowledge_point, analysis, answer, options_json, created_by
)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 20
)
SELECT
    730000300000 + n AS id,
    'JUDGE' AS type,
    CONCAT(
        '[JAVA-JUDGE-', LPAD(n, 2, '0'), '] ',
        CASE ((n - 1) % 10)
            WHEN 0 THEN 'String is immutable.'
            WHEN 1 THEN 'HashMap is thread-safe.'
            WHEN 2 THEN 'Without abnormal JVM shutdown, finally usually executes.'
            WHEN 3 THEN 'Operator == compares object content for String.'
            WHEN 4 THEN 'JDK8 ConcurrentHashMap still uses Segment locks.'
            WHEN 5 THEN 'Static methods cannot be overridden, only hidden.'
            WHEN 6 THEN 'try-with-resources can auto-close AutoCloseable resources.'
            WHEN 7 THEN 'Default Integer cache range includes value 100.'
            WHEN 8 THEN 'Java supports multiple inheritance for classes.'
            ELSE 'volatile provides visibility but not compound-operation atomicity.'
        END
    ) AS stem,
    1 + ((n - 1) % 5) AS difficulty,
    CASE ((n - 1) % 10)
        WHEN 0 THEN 'String'
        WHEN 1 THEN 'Collections Concurrency'
        WHEN 2 THEN 'Exception Handling'
        WHEN 3 THEN 'Object Comparison'
        WHEN 4 THEN 'Concurrent Containers'
        WHEN 5 THEN 'OOP'
        WHEN 6 THEN 'Resource Management'
        WHEN 7 THEN 'Wrapper Types'
        WHEN 8 THEN 'Inheritance'
        ELSE 'JMM'
    END AS knowledge_point,
    CASE ((n - 1) % 10)
        WHEN 0 THEN 'String content cannot be changed after creation.'
        WHEN 1 THEN 'HashMap is not thread-safe.'
        WHEN 2 THEN 'finally generally runs except in hard-stop scenarios.'
        WHEN 3 THEN '== compares references; equals compares content.'
        WHEN 4 THEN 'JDK8 removed Segment lock structure.'
        WHEN 5 THEN 'Static method dispatch is class-based.'
        WHEN 6 THEN 'Resources are closed automatically after statement ends.'
        WHEN 7 THEN 'Default Integer cache is from -128 to 127.'
        WHEN 8 THEN 'Classes do not support multiple inheritance in Java.'
        ELSE 'volatile cannot make i++ atomic.'
    END AS analysis,
    CASE ((n - 1) % 10)
        WHEN 0 THEN 'true'
        WHEN 1 THEN 'false'
        WHEN 2 THEN 'true'
        WHEN 3 THEN 'false'
        WHEN 4 THEN 'false'
        WHEN 5 THEN 'true'
        WHEN 6 THEN 'true'
        WHEN 7 THEN 'true'
        WHEN 8 THEN 'false'
        ELSE 'true'
    END AS answer,
    JSON_ARRAY() AS options_json,
    21001 AS created_by
FROM seq;

INSERT IGNORE INTO q_question (
    id, type, stem, difficulty, knowledge_point, analysis, answer, options_json, created_by
)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 20
)
SELECT
    730000400000 + n AS id,
    'FILL' AS type,
    CONCAT(
        '[JAVA-FILL-', LPAD(n, 2, '0'), '] ',
        CASE ((n - 1) % 10)
            WHEN 0 THEN 'A common Map implementation that allows null key is ____.'
            WHEN 1 THEN 'The JVM memory area for local variables is ____.'
            WHEN 2 THEN 'The method to wait for a thread to finish is ____.'
            WHEN 3 THEN 'The annotation for functional interface is @____.'
            WHEN 4 THEN 'The Stream API method for filtering is ____.'
            WHEN 5 THEN 'A keyword used in DCL singleton for safe publication is ____.'
            WHEN 6 THEN 'In Spring, a common annotation for by-type injection is @____.'
            WHEN 7 THEN 'In JDBC, DriverManager.____() is used to create connection.'
            WHEN 8 THEN 'The upper-bound wildcard syntax is ? ____ Number.'
            ELSE 'ExecutorService method for submitting a task with return value is ____.'
        END
    ) AS stem,
    1 + ((n - 1) % 5) AS difficulty,
    CASE ((n - 1) % 10)
        WHEN 0 THEN 'Collections'
        WHEN 1 THEN 'JVM'
        WHEN 2 THEN 'Concurrency'
        WHEN 3 THEN 'Functional Programming'
        WHEN 4 THEN 'Stream'
        WHEN 5 THEN 'Concurrency'
        WHEN 6 THEN 'Spring'
        WHEN 7 THEN 'JDBC'
        WHEN 8 THEN 'Generics'
        ELSE 'Thread Pool'
    END AS knowledge_point,
    CASE ((n - 1) % 10)
        WHEN 0 THEN 'HashMap supports one null key and multiple null values.'
        WHEN 1 THEN 'JVM stack is thread-private.'
        WHEN 2 THEN 'join waits for target thread termination.'
        WHEN 3 THEN 'Use @FunctionalInterface for functional interfaces.'
        WHEN 4 THEN 'filter is used for conditional selection.'
        WHEN 5 THEN 'volatile helps avoid reordering in DCL pattern.'
        WHEN 6 THEN '@Autowired is commonly used in Spring.'
        WHEN 7 THEN 'DriverManager.getConnection is the common JDBC entry.'
        WHEN 8 THEN 'Upper bound wildcard uses extends.'
        ELSE 'submit returns a Future for callable tasks.'
    END AS analysis,
    CASE ((n - 1) % 10)
        WHEN 0 THEN 'HashMap'
        WHEN 1 THEN 'JVM stack'
        WHEN 2 THEN 'join'
        WHEN 3 THEN 'FunctionalInterface'
        WHEN 4 THEN 'filter'
        WHEN 5 THEN 'volatile'
        WHEN 6 THEN 'Autowired'
        WHEN 7 THEN 'getConnection'
        WHEN 8 THEN 'extends'
        ELSE 'submit'
    END AS answer,
    JSON_ARRAY() AS options_json,
    21001 AS created_by
FROM seq;

INSERT IGNORE INTO q_question (
    id, type, stem, difficulty, knowledge_point, analysis, answer, options_json, created_by
)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 20
)
SELECT
    730000500000 + n AS id,
    'SHORT' AS type,
    CONCAT(
        '[JAVA-SHORT-', LPAD(n, 2, '0'), '] ',
        CASE ((n - 1) % 10)
            WHEN 0 THEN 'Explain key differences between HashMap and ConcurrentHashMap.'
            WHEN 1 THEN 'Explain equals/hashCode contract and impacts if violated.'
            WHEN 2 THEN 'Explain responsibilities of JVM heap and stack.'
            WHEN 3 THEN 'Compare String, StringBuilder and StringBuffer.'
            WHEN 4 THEN 'Compare synchronized and ReentrantLock.'
            WHEN 5 THEN 'Describe major phases of Java class loading.'
            WHEN 6 THEN 'What is GC Roots and how reachability analysis works?'
            WHEN 7 THEN 'Compare interface and abstract class design tradeoffs.'
            WHEN 8 THEN 'Describe thread pool core parameters and tuning approach.'
            ELSE 'Summarize common strategies to prevent NullPointerException.'
        END
    ) AS stem,
    1 + ((n - 1) % 5) AS difficulty,
    CASE ((n - 1) % 10)
        WHEN 0 THEN 'Concurrent Containers'
        WHEN 1 THEN 'Object Model'
        WHEN 2 THEN 'JVM'
        WHEN 3 THEN 'String'
        WHEN 4 THEN 'Concurrency'
        WHEN 5 THEN 'Class Loading'
        WHEN 6 THEN 'Garbage Collection'
        WHEN 7 THEN 'OOP Design'
        WHEN 8 THEN 'Thread Pool'
        ELSE 'Code Robustness'
    END AS knowledge_point,
    CASE ((n - 1) % 10)
        WHEN 0 THEN 'Scoring focus: thread safety mechanism, performance, and usage scenarios.'
        WHEN 1 THEN 'Scoring focus: contract definition, collection behavior impact, examples.'
        WHEN 2 THEN 'Scoring focus: memory role, lifecycle, and sharing model.'
        WHEN 3 THEN 'Scoring focus: mutability, thread safety, and performance.'
        WHEN 4 THEN 'Scoring focus: lock features, interruptibility, fairness, conditions.'
        WHEN 5 THEN 'Scoring focus: loading, verification, preparation, resolution, initialization.'
        WHEN 6 THEN 'Scoring focus: GC roots categories and reachability flow.'
        WHEN 7 THEN 'Scoring focus: abstraction style, reuse, extensibility.'
        WHEN 8 THEN 'Scoring focus: core/max/queue/rejection and pressure testing.'
        ELSE 'Scoring focus: null checks, Optional, validation, and testing.'
    END AS analysis,
    CASE ((n - 1) % 10)
        WHEN 0 THEN 'HashMap is not thread-safe while ConcurrentHashMap is thread-safe with better concurrency performance.'
        WHEN 1 THEN 'Objects equal by equals must return same hashCode, otherwise hash-based collections behave incorrectly.'
        WHEN 2 THEN 'Heap stores objects and is managed by GC; stack stores frames and local variables per thread.'
        WHEN 3 THEN 'String is immutable; StringBuilder is mutable and not thread-safe; StringBuffer is mutable and thread-safe.'
        WHEN 4 THEN 'synchronized is JVM-managed and simple; ReentrantLock offers richer features like interruptible lock and fairness.'
        WHEN 5 THEN 'Class loading phases typically include loading, verification, preparation, resolution and initialization.'
        WHEN 6 THEN 'Reachability analysis starts from GC Roots; unreachable objects become GC candidates.'
        WHEN 7 THEN 'Interface is contract-oriented and flexible; abstract class is good for shared state and base behavior.'
        WHEN 8 THEN 'Thread pool tuning balances core size, max size, queue capacity and rejection policy with load tests.'
        ELSE 'Prevent NPE by null checks, defensive coding, Optional usage, validation and unit tests.'
    END AS answer,
    JSON_ARRAY() AS options_json,
    21001 AS created_by
FROM seq;
