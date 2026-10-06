# Number Options

The factory decides the Java type of the numbers: a subclass of `JavaJsonFactory` can make
every decimal a `BigDecimal`, or every integer a `Long`. The YAML parser uses the same
configuration.
