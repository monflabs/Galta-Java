# Northwind Demo Database

The classic Northwind trading company database (customers, employees,
orders, products, suppliers, ...): one CSV file per table in
`northwind/csv/`, and the PostgreSQL scripts to create and fill the schema in
`northwind/postgresql/`.

```java
NorthwindTables tables = new NorthwindTables();
_Table<Orders.Record> orders = tables.getTable(NorthwindTables.orders);
```

How the CSV values are read (see `NorthwindTables`):

- an empty field is a SQL `NULL` and becomes `null`;
- the escaped line breaks of the employee addresses become real line breaks;
- the identifiers keep the types of the record classes - mostly `String`
  (including numeric ones such as `order_id`), `int` for the shipper
  (`Shippers.shipper_id`, `Orders.ship_via`): the record classes are shared
  with other projects, so these types are not changed;
- the dates stay ISO-8601 strings (`yyyy-MM-dd`);
- `customer_customer_demo` and `customer_demographics` only have a header row:
  their tables are empty.

## Source and license

| | |
|---|---|
| Source | the PostgreSQL port described in https://blog.yugabyte.com/how-to-the-northwind-postgresql-sample-database-running-on-a-distributed-sql-database/ (see also https://en.wikiversity.org/wiki/Database_Examples/Northwind) |
| Origin | Microsoft's Northwind sample database |
| License | to be confirmed |

The module is not published to Maven Central until the license is reviewed.
