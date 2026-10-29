// Util for comparing two nested JSON values (arrays inside arrays, objects inside arrays, ...)
// and listing where they differ. Not a spec: Playwright only runs *.spec.js files.

/**
 * Compares two nested JSON values.
 * Walks both with a stack instead of recursion, so very deep nesting can't overflow the call stack.
 *
 * @param {*} json1
 * @param {*} json2
 * @param {{ ignoreKeys?: string[] }} [options]
 *   ignoreKeys: property names whose values change between runs (IDs, timestamps, ...).
 *   They are skipped at every nesting level, and are not reported if missing on one side.
 * @returns {{ equal: boolean, differences: string[] }}
 *
 * @example
 * const { compareNestedJSONs } = require('./utils/compare-nested-jsons');
 *
 * compareNestedJSONs([[1, [2, { A: 'run-101', id: 'x' }]]], [[1, [2, { A: 'run-202', id: 'x' }]]], { ignoreKeys: ['A'] });
 * // { equal: true, differences: [] }
 *
 * compareNestedJSONs([[1, [4, 5]]], [[1, [4, 6]]]);
 * // { equal: false, differences: ['$[0][1][1]: 5 vs 6'] }
 */
function compareNestedJSONs(json1, json2, options = {}) {
  const ignoredKeys = new Set(options.ignoreKeys || []);
  const typeOf = (value) => (value === null ? 'null' : Array.isArray(value) ? 'array' : typeof value);
  const differences = [];
  // Each entry is one pair of values still to compare, with its path, e.g. "$[0][2][1]"
  const stack = [{ left: json1, right: json2, path: '$' }];

  while (stack.length > 0) {
    const { left, right, path } = stack.pop();
    const leftType = typeOf(left);
    const rightType = typeOf(right);

    if (leftType !== rightType) {
      differences.push(`${path}: type ${leftType} vs ${rightType}`);
    } else if (leftType === 'array') {
      if (left.length !== right.length) {
        differences.push(`${path}: length ${left.length} vs ${right.length}`);
      }
      // Compare the items both arrays have; push in reverse so they come off the stack in order
      for (let i = Math.min(left.length, right.length) - 1; i >= 0; i--) {
        stack.push({ left: left[i], right: right[i], path: `${path}[${i}]` });
      }
    } else if (leftType === 'object') {
      const keys = [...new Set([...Object.keys(left), ...Object.keys(right)])]
        .filter((key) => !ignoredKeys.has(key)) // dynamic properties are not compared
        .reverse();
      for (const key of keys) {
        if (!(key in right)) {
          differences.push(`${path}.${key}: only in first JSON`);
        } else if (!(key in left)) {
          differences.push(`${path}.${key}: only in second JSON`);
        } else {
          stack.push({ left: left[key], right: right[key], path: `${path}.${key}` });
        }
      }
    } else if (left !== right) {
      differences.push(`${path}: ${JSON.stringify(left)} vs ${JSON.stringify(right)}`);
    }
  }

  return { equal: differences.length === 0, differences };
}

module.exports = { compareNestedJSONs };
