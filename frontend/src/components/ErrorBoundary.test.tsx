import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ErrorBoundary } from "./ErrorBoundary";

function BrokenPanel() {
  throw new Error("Timeline render failed");
  return null;
}

describe("ErrorBoundary", () => {
  it("shows a page-level fallback when rendering fails", () => {
    vi.spyOn(console, "error").mockImplementation(() => undefined);

    render(
      <ErrorBoundary>
        <BrokenPanel />
      </ErrorBoundary>
    );

    expect(screen.getByRole("heading", { name: "Console error" })).toBeInTheDocument();
    expect(screen.getByText("Timeline render failed")).toBeInTheDocument();
  });
});
