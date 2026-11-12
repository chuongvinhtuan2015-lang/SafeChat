const admin = require("firebase-admin");
const serviceAccount = require("./service-account.json");

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

console.log("==========================================");
console.log(" SafeChat FCM Server đang chạy...");
console.log(" Đang lắng nghe tin nhắn mới từ Firestore...");
console.log("==========================================");

const serverStartTime = Date.now();

// Lắng nghe trực tiếp qua collectionGroup
db.collectionGroup("messages").onSnapshot((snapshot) => {
  snapshot.docChanges().forEach(async (change) => {
    if (change.type === "added") {
      const msg = change.doc.data();

      // Lọc bỏ tin nhắn cũ
      if (!msg.timestamp || msg.timestamp <= serverStartTime) {
        return;
      }

      const receiverId = msg.receiverId;
      const senderId = msg.senderId;

      if (!receiverId || !senderId) return;

      try {
        // Sử dụng .doc() thay vì .document()
        const receiverDoc = await db.collection("users").doc(receiverId).get();
        if (!receiverDoc.exists) {
          console.log(`[Bỏ qua] Không tìm thấy user: ${receiverId}`);
          return;
        }

        const fcmToken = receiverDoc.data().fcmToken;
        if (!fcmToken) {
          console.log(`[Bỏ qua] User nhận (${receiverId}) chưa có FCM Token.`);
          return;
        }

        let senderName = "SafeChat";
        const senderDoc = await db.collection("users").doc(senderId).get();
        if (senderDoc.exists && senderDoc.data().fullName) {
          senderName = senderDoc.data().fullName;
        }

        const payload = {
          token: fcmToken,
          data: {
            senderId: String(senderId),
            senderName: String(senderName),
            type: "NEW_MESSAGE"
          },
          android: {
            priority: "high"
          }
        };

        const response = await admin.messaging().send(payload);
        console.log(`[FCM Đã Gửi] Từ: ${senderName} -> User: ${receiverId} | MessageID: ${response}`);
      } catch (error) {
        console.error("[Lỗi Gửi FCM]:", error.message);
      }
    }
  });
}, (error) => {
  console.error("[Lỗi Firestore Listener]:", error);
});