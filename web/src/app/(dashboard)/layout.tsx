import type { ReactNode } from "react";

const SCREENS = [
  { href: "/overview", label: "Vue d'ensemble" },
  { href: "/outings", label: "Sorties" },
  { href: "/costs", label: "Coûts" },
  { href: "/imports", label: "Imports" },
  { href: "/fiscal", label: "Fiscal" },
  { href: "/audit", label: "Journal" },
] as const;

export default function DashboardLayout({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-dvh">
      <nav aria-label="Navigation principale" className="border-b border-neutral-800">
        <ul className="flex flex-wrap gap-1 p-2">
          {SCREENS.map(({ href, label }) => (
            <li key={href}>
              <a
                href={href}
                className="block rounded px-3 py-2 text-sm hover:bg-neutral-800 focus-visible:outline-2"
              >
                {label}
              </a>
            </li>
          ))}
        </ul>
      </nav>
      <main className="p-4">{children}</main>
    </div>
  );
}
