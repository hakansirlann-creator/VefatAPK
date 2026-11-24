const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_hakan";
const USER_UID = "reader_user";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read manuel_vefat, cache/belediye, or ayarlar/reklam", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("manuel_vefat").where("yayinda", "==", true).get());
  await assertFails(unauthDb.collection("cache").doc("belediye").get());
  await assertFails(unauthDb.collection("ayarlar").doc("reklam").get());
});

test("Signed-in non-admin can read published manuel_vefat with yayinda==true filter, but cannot read unfiltered or create", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("manuel_vefat").doc("kayit1").set({
      adSoyad: "Ahmet YILMAZ",
      vefatTarihi: "15.11.2026",
      vefatTarihiIso: "2026-11-15",
      yayinda: true,
      ekleyenUid: ADMIN_UID,
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const userDb = testEnv.authenticatedContext(USER_UID, {
    email: "okuyucu@gmail.com",
    email_verified: true,
  }).firestore();

  await assertSucceeds(userDb.collection("manuel_vefat").where("yayinda", "==", true).get());
  await assertFails(userDb.collection("manuel_vefat").get());
  await assertFails(
    userDb.collection("manuel_vefat").doc("kayit2").set({
      adSoyad: "Yetkisiz Kayıt",
      vefatTarihi: "15.11.2026",
      vefatTarihiIso: "2026-11-15",
      yayinda: true,
      ekleyenUid: USER_UID,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("Admin with verified email can create, update, and delete manuel_vefat, but unverified email fails", async () => {
  const unverifiedAdminDb = testEnv.authenticatedContext("spoof_uid", {
    email: "hakansirlann@gmail.com",
    email_verified: false,
  }).firestore();

  await assertFails(
    unverifiedAdminDb.collection("manuel_vefat").doc("kayit_spoof").set({
      adSoyad: "Sahte Admin",
      vefatTarihi: "15.11.2026",
      vefatTarihiIso: "2026-11-15",
      yayinda: true,
      ekleyenUid: "spoof_uid",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "hakansirlann@gmail.com",
    email_verified: true,
  }).firestore();

  await assertSucceeds(
    adminDb.collection("manuel_vefat").doc("kayit_ok").set({
      adSoyad: "Mehmet KAYA",
      dogumTarihi: "1952",
      dogumYeri: "Batman",
      vefatTarihi: "15.11.2026",
      vefatTarihiIso: "2026-11-15",
      definYeri: "Asri Mezarlık",
      definTarihi: "15.11.2026",
      cenazeYakini: "Ali KAYA",
      yakinTelefonu: "05321112233",
      taziyeYeri: "Kültür Mahallesi Taziye Evi",
      taziyeTarihi: "15-17.11.2026",
      yayinda: true,
      ekleyenUid: ADMIN_UID,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertFails(
    adminDb.collection("manuel_vefat").doc("kayit_ghost").set({
      adSoyad: "Hayalet Alan",
      vefatTarihi: "15.11.2026",
      vefatTarihiIso: "2026-11-15",
      yayinda: true,
      ekleyenUid: ADMIN_UID,
      ghostField: "bad",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(
    adminDb.collection("manuel_vefat").doc("kayit_ok").update({
      taziyeYeri: "Yeni Taziye Evi",
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(adminDb.collection("manuel_vefat").doc("kayit_ok").delete());
});

test("Signed-in user can update cache/belediye but non-admin cannot write ayarlar/reklam", async () => {
  const userDb = testEnv.authenticatedContext(USER_UID, {
    email: "okuyucu@gmail.com",
    email_verified: true,
  }).firestore();

  await assertSucceeds(
    userDb.collection("cache").doc("belediye").set({
      kayitlar: [{ adSoyad: "Test Kişi", vefatTarihi: "15.11.2026" }],
      guncellenmeZamaniMs: Date.now(),
      guncelleyenUid: USER_UID,
      kaynak: "batman.bel.tr",
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(userDb.collection("cache").doc("belediye").get());

  await assertFails(
    userDb.collection("ayarlar").doc("reklam").set({
      aktif: true,
      baslik: "Reklam",
      metin: "Test",
      arkaPlanRengi: "#1B4332",
      guncelleyenUid: USER_UID,
      updatedAt: new Date(),
    })
  );

  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "hakansirlann@gmail.com",
    email_verified: true,
  }).firestore();

  await assertSucceeds(
    adminDb.collection("ayarlar").doc("reklam").set({
      aktif: true,
      baslik: "Batman Taziye Yemek Hizmeti",
      metin: "Taziye yemeği ve ikram hizmeti – Bizi arayın",
      arkaPlanRengi: "#1B4332",
      telefon: "04882120000",
      guncelleyenUid: ADMIN_UID,
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(userDb.collection("ayarlar").doc("reklam").get());
});

test("Admin can publish bildirimler and signed-in user can read them, while non-admin cannot write", async () => {
  const userDb = testEnv.authenticatedContext(USER_UID, {
    email: "okuyucu@gmail.com",
    email_verified: true,
  }).firestore();

  await assertFails(
    userDb.collection("bildirimler").doc("b1").set({
      baslik: "Yeni Vefat",
      mesaj: "Yetkisiz bildirim",
      adSoyad: "Test Kişi",
      yayinda: true,
      olusturmaMs: Date.now(),
      gonderenUid: USER_UID,
      createdAt: new Date(),
    })
  );

  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "hakansirlann@gmail.com",
    email_verified: true,
  }).firestore();

  await assertSucceeds(
    adminDb.collection("bildirimler").doc("b1").set({
      baslik: "Yeni Vefat İlanı",
      mesaj: "Ahmet YILMAZ vefat etti. Taziye: Kültür Taziye Evi",
      adSoyad: "Ahmet YILMAZ",
      vefatKayitId: "kayit1",
      yayinda: true,
      olusturmaMs: Date.now(),
      gonderenUid: ADMIN_UID,
      createdAt: new Date(),
    })
  );

  await assertSucceeds(userDb.collection("bildirimler").where("yayinda", "==", true).get());
  await assertFails(userDb.collection("bildirimler").get());
});

test("Signed-in user can log own profile & login event, but ONLY admin can list users or read login logs", async () => {
  const userDb = testEnv.authenticatedContext(USER_UID, {
    email: "okuyucu@gmail.com",
    email_verified: true,
  }).firestore();

  const now = Date.now();
  await assertSucceeds(
    userDb.collection("kullanicilar").doc(USER_UID).set({
      uid: USER_UID,
      email: "okuyucu@gmail.com",
      adSoyad: "Okuyucu Kullanıcı",
      cihaz: "Pixel",
      aktif: true,
      ilkGirisMs: now,
      sonGirisMs: now,
      sonAktifMs: now,
      girisSayisi: 1,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // User can read own profile and create own login log, but cannot impersonate another UID
  await assertSucceeds(userDb.collection("kullanicilar").doc(USER_UID).get());
  await assertFails(userDb.collection("kullanicilar").doc("other_uid").get());
  await assertFails(userDb.collection("kullanicilar").get());

  await assertSucceeds(
    userDb.collection("giris_loglari").doc("log1").set({
      uid: USER_UID,
      email: "okuyucu@gmail.com",
      adSoyad: "Okuyucu Kullanıcı",
      cihaz: "Pixel",
      olay: "GOOGLE_GIRIS",
      zamanMs: now,
      createdAt: new Date(),
    })
  );

  await assertFails(
    userDb.collection("giris_loglari").doc("log_spoof").set({
      uid: "other_uid",
      olay: "GOOGLE_GIRIS",
      zamanMs: now,
      createdAt: new Date(),
    })
  );

  // Non-admin CANNOT read or list login logs
  await assertFails(userDb.collection("giris_loglari").doc("log1").get());
  await assertFails(userDb.collection("giris_loglari").get());

  // Verified Admin CAN list all users (active + installed count) and read login logs
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "hakansirlann@gmail.com",
    email_verified: true,
  }).firestore();

  await assertSucceeds(adminDb.collection("kullanicilar").get());
  await assertSucceeds(adminDb.collection("giris_loglari").get());
});

