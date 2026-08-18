import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import DashboardLayout from "../layout";

describe("DashboardLayout", () => {
  it("expose les six écrans du périmètre v1", () => {
    render(<DashboardLayout>{null}</DashboardLayout>);
    const nav = screen.getByRole("navigation", { name: "Navigation principale" });
    expect(nav).toBeInTheDocument();
    expect(screen.getAllByRole("link")).toHaveLength(6);
  });

  it("n'expose pas l'écran Plateformes, hors périmètre v1 (ADR-008)", () => {
    render(<DashboardLayout>{null}</DashboardLayout>);
    expect(screen.queryByRole("link", { name: /plateforme/i })).toBeNull();
  });
});
