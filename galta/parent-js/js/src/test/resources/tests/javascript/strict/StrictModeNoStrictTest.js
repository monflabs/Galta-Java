// Always strict inside
import './StrictModule.js';

function isStrictMode() {
  return this === undefined;
}

assertFalse(isStrictMode())
