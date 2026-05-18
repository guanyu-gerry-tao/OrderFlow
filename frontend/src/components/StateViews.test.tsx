import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { EmptyState, ErrorState, StatusPill } from "./StateViews";

describe("StateViews", () => {
  it("renders empty state copy", () => {
    render(<EmptyState title="No failed events" body="The DLQ is empty." />);

    expect(screen.getByText("No failed events")).toBeInTheDocument();
    expect(screen.getByText("The DLQ is empty.")).toBeInTheDocument();
  });

  it("calls retry handler from recoverable error state", async () => {
    const retry = vi.fn();
    render(<ErrorState title="Backend unavailable" message="Connection refused" onRetry={retry} />);

    await userEvent.click(screen.getByRole("button", { name: "Retry" }));

    expect(retry).toHaveBeenCalledTimes(1);
  });

  it("applies the requested status tone", () => {
    render(<StatusPill tone="danger">OPEN</StatusPill>);

    expect(screen.getByText("OPEN")).toHaveClass("status-danger");
  });
});
