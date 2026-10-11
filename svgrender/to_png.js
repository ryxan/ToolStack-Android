const sharp = require('sharp');
const [inSvg, outPng] = process.argv.slice(2);
sharp(inSvg).png().toFile(outPng)
  .then(i => console.log('ok', i.width, i.height))
  .catch(e => { console.log('ERR', e.message); process.exit(1); });
