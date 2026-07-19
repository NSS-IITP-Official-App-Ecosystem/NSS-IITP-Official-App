const body = {
    eventId: "test",
    positiveRollNumbers: [],
    negativeRollNumbers: [],
    zeroRollNumbers: ["2501EE04", "2501EE18"]
};

const positiveRollNumbers = body.positiveRollNumbers;
const negativeRollNumbers = body.negativeRollNumbers;
const zeroRollNumbers = body.zeroRollNumbers;

const hasSelectiveLists = Array.isArray(positiveRollNumbers) || Array.isArray(negativeRollNumbers) || Array.isArray(zeroRollNumbers);
console.log("hasSelectiveLists:", hasSelectiveLists);

const posSet = new Set((positiveRollNumbers || []).map(r => r.toUpperCase()));
const negSet = new Set((negativeRollNumbers || []).map(r => r.toUpperCase()));
const zeroSet = new Set((zeroRollNumbers || []).map(r => r.toUpperCase()));

console.log("posSet size:", posSet.size);
console.log("negSet size:", negSet.size);
console.log("zeroSet size:", zeroSet.size);
