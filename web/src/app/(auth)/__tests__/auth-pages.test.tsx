import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import LoginPage from "../login/page";
import RegisterPage from "../register/page";

const request = vi.hoisted(() => ({ localeCookie: undefined as string | undefined }));

vi.mock("next/headers", () => ({
  cookies: async () => ({
    get: (name: string) =>
      request.localeCookie === undefined ? undefined : { name, value: request.localeCookie },
  }),
  headers: async () => new Headers(),
}));

beforeEach(() => {
  request.localeCookie = undefined;
});

afterEach(cleanup);

describe("authentication screens", () => {
  it("renders the sign-in title in French by default", async () => {
    render(await LoginPage());

    expect(screen.getByRole("heading", { name: "Connexion" })).toBeInTheDocument();
    expect(screen.getByText(/Route en place, écran non implémenté — KDN-19\./u)).toBeInTheDocument();
  });

  it("renders the sign-in title in English when the locale cookie says so", async () => {
    request.localeCookie = "en";
    render(await LoginPage());

    expect(screen.getByRole("heading", { name: "Sign in" })).toBeInTheDocument();
    expect(
      screen.getByText(/Route in place, screen not implemented yet — KDN-19\./u),
    ).toBeInTheDocument();
    expect(screen.queryByText("Connexion")).toBeNull();
  });

  it("renders the sign-up title in both languages", async () => {
    render(await RegisterPage());
    expect(screen.getByRole("heading", { name: "Inscription" })).toBeInTheDocument();
    cleanup();

    request.localeCookie = "en";
    render(await RegisterPage());
    expect(screen.getByRole("heading", { name: "Sign up" })).toBeInTheDocument();
  });
});
