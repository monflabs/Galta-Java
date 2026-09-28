// Template literals
const firstName = 'John';
console.log(`Hello ${firstName}!
    Good morning!`);

// Tagged template literals
function styled(strings, ...values) {
    return strings.reduce((css, str, i) => css + (values[i - 1] ?? '') + str);
}
const buttonStyle = styled`
      display: inline-block;
      border-radius: 3px;
    `;
console.log(buttonStyle);