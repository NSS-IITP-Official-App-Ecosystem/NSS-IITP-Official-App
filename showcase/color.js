const fs = require('fs');
const override = (file, color) => {
  let content = fs.readFileSync(file, 'utf8');
  content = content.replace(/<path d=/g, `<path fill="${color}" d=`);
  fs.writeFileSync(file, content);
};
override('public/compose.svg', '#4285F4');
override('public/android.svg', '#3DDC84');
console.log('done');
