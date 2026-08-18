import { describe, expect, it } from "vitest";

import { cn } from "@/lib/utils";

describe("cn", () => {
  it("concatène les classes", () => {
    expect(cn("px-2", "py-1")).toBe("px-2 py-1");
  });

  it("laisse la dernière classe l'emporter sur un conflit Tailwind", () => {
    expect(cn("px-2", "px-4")).toBe("px-4");
  });

  it("écarte les valeurs conditionnelles fausses", () => {
    expect(cn("px-2", false && "hidden", undefined)).toBe("px-2");
  });
});
