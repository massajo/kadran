import { getTranslations } from "@/lib/i18n/locale";

export default async function OverviewPage() {
  const t = await getTranslations();

  return (
    <section>
      <h1 className="text-lg font-medium">{t("nav.overview")}</h1>
      <p className="mt-2 text-sm text-neutral-400">
        {t("screen.notImplemented", { issue: "KDN-96" })}
      </p>
    </section>
  );
}
