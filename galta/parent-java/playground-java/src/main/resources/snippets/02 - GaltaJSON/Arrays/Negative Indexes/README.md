# Negative Indexes

Every index method of `JsonArray` has an `at` counterpart that also takes a negative
index, counted from the end: `-1` is the last item. A `get` method becomes `at`
(`getString` is `atString`), the others take an `At` suffix (`addAt`, `setAt`, `removeAt`).
