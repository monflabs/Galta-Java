// A module is always strict!
function isStrictMode() {
  return this === undefined;
}

assertTrue(isStrictMode())
