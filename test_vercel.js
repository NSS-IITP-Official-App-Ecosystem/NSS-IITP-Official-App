fetch("https://nss-app-notif.vercel.app/api/send", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({
    projectId: "chatapp-24fae",
    topic: "test",
    title: "Vercel Test Validation"
  })
}).then(async r => {
  console.log("Status:", r.status);
  console.log("Text:", await r.text());
}).catch(console.error);
