"use strict"

function isStrictMode() {
  return this === undefined;
}

assertTrue(isStrictMode())