import { getTranslations } from "@/lib/i18n/locale";

export default async function OutingsPage() {
  const t = await getTranslations();

  return (
    <section>
      <h1 className="text-lg font-medium">{t("nav.outings")}</h1>
      <p className="mt-2 text-sm text-neutral-400">
        {t("screen.notImplemented", { issue: "KDN-41" })}
      </p>
    </section>
  );
}
