import { getTranslations } from "@/lib/i18n/locale";

export default async function RegisterPage() {
  const t = await getTranslations();

  return (
    <main className="p-4">
      <h1 className="text-lg font-medium">{t("auth.register.title")}</h1>
      <p className="mt-2 text-sm text-neutral-400">
        {t("screen.notImplemented", { issue: "KDN-19" })}
      </p>
    </main>
  );
}
