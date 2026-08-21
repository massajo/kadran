import { cleanup, render, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { AppIntlProvider } from "@/lib/i18n/intl-provider";
import { resolveLocale } from "@/lib/i18n/locale";

import DashboardLayout from "../layout";

/** Requête simulée : ce que le layout lira via `next/headers`. */
const request = vi.hoisted(() => ({
  localeCookie: undefined as string | undefined,
  acceptLanguage: undefined as string | undefined,
}));

vi.mock("next/headers", () => ({
  cookies: async () => ({
    get: (name: string) =>
      request.localeCookie === undefined ? undefined : { name, value: request.localeCookie },
  }),
  headers: async () =>
    new Headers(
      request.acceptLanguage === undefined ? {} : { "accept-language": request.acceptLanguage },
    ),
}));

// L'action serveur du sélecteur touche au cookie et au cache de Next : hors de propos ici.
vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));

/**
 * Rend le layout comme Next le fait : le Server Component est résolu, puis placé sous le
 * provider que pose le layout racine — c'est lui qui alimente les Client Components.
 */
async function renderDashboard() {
  const locale = await resolveLocale();
  render(<AppIntlProvider locale={locale}>{await DashboardLayout({ children: null })}</AppIntlProvider>);
  return locale;
}

function navigationLinks() {
  return within(screen.getByRole("navigation")).getAllByRole("link");
}

beforeEach(() => {
  request.localeCookie = undefined;
  request.acceptLanguage = undefined;
});

afterEach(cleanup);

describe("DashboardLayout", () => {
  it("exposes the six screens of the v1 scope", async () => {
    await renderDashboard();

    expect(screen.getByRole("navigation", { name: "Navigation principale" })).toBeInTheDocument();
    expect(navigationLinks()).toHaveLength(6);
  });

  it("does not expose the Platforms screen, out of the v1 scope (ADR-008)", async () => {
    await renderDashboard();

    expect(screen.queryByRole("link", { name: /plateforme|platform/i })).toBeNull();
  });

  it("renders the navigation in French by default", async () => {
    expect(await renderDashboard()).toBe("fr");

    expect(screen.getByRole("navigation", { name: "Navigation principale" })).toBeInTheDocument();
    expect(navigationLinks().map((link) => link.textContent)).toEqual([
      "Vue d'ensemble",
      "Sorties",
      "Coûts",
      "Imports",
      "Fiscal",
      "Journal",
    ]);
  });

  it("renders the navigation in English when the locale cookie says so", async () => {
    request.localeCookie = "en";

    expect(await renderDashboard()).toBe("en");

    expect(screen.getByRole("navigation", { name: "Main navigation" })).toBeInTheDocument();
    expect(navigationLinks().map((link) => link.textContent)).toEqual([
      "Overview",
      "Outings",
      "Costs",
      "Imports",
      "Tax",
      "Audit log",
    ]);
  });

  it("falls back to Accept-Language when no cookie has been set yet", async () => {
    request.acceptLanguage = "en-GB,en;q=0.9";

    expect(await renderDashboard()).toBe("en");
    expect(screen.getByRole("link", { name: "Overview" })).toBeInTheDocument();
  });

  it("lets a stored cookie win over the browser header", async () => {
    request.localeCookie = "fr";
    request.acceptLanguage = "en-GB,en;q=0.9";

    expect(await renderDashboard()).toBe("fr");
    expect(screen.getByRole("link", { name: "Vue d'ensemble" })).toBeInTheDocument();
  });

  it("keeps one canonical URL per screen, whatever the language", async () => {
    await renderDashboard();
    const french = navigationLinks().map((link) => link.getAttribute("href"));
    cleanup();

    request.localeCookie = "en";
    await renderDashboard();
    const english = navigationLinks().map((link) => link.getAttribute("href"));

    expect(english).toEqual(french);
    expect(english).toEqual([
      "/overview",
      "/outings",
      "/costs",
      "/imports",
      "/fiscal",
      "/audit",
    ]);
  });

  it("offers a switch to the other language, marking the current one", async () => {
    await renderDashboard();

    const french = screen.getByRole("button", { name: "Français" });
    const english = screen.getByRole("button", { name: "English" });

    expect(french).toHaveAttribute("aria-current", "true");
    expect(english).not.toHaveAttribute("aria-current");
    expect(english).toHaveAttribute("value", "en");
  });

  it("carries no hard-coded label — every screen name comes from the catalogue", async () => {
    request.localeCookie = "en";
    await renderDashboard();

    // « Sorties » est le libellé français : s'il subsistait en anglais, il serait en dur.
    expect(screen.queryByText("Sorties")).toBeNull();
    expect(screen.queryByText("Vue d'ensemble")).toBeNull();
  });
});
