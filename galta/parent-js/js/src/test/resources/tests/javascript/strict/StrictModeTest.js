// Always struict inside
import './StrictModule.js';

function isStrictMode() {
  return this === undefined;
}

assertTrue(isStrictMode())
