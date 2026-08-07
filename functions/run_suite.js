const { spawnSync } = require('child_process');

const testFiles = [
    'test/attendance.test.js',
    'test/meta.test.js',
    'test/auth.test.js',
    'test/validation.test.js',
    'test/scheduled.test.js'
];

let hasError = false;

for (const file of testFiles) {
    console.log(`\n========================================`);
    console.log(`🚀 Running ${file}...`);
    console.log(`========================================\n`);
    
    const result = spawnSync('node', ['--test', file], { stdio: 'inherit' });
    
    if (result.status !== 0) {
        console.error(`\n❌ Tests failed in ${file}`);
        hasError = true;
        break;
    } else {
        console.log(`\n✅ Passed ${file}`);
    }
}

if (hasError) {
    console.error('\n💥 Test suite failed.');
    process.exit(1);
} else {
    console.log('\n🎉 All tests passed successfully!');
    process.exit(0);
}
