import type { HTMLAttributes, PropsWithChildren } from "react";
import { Card } from "./Card";

type Props = PropsWithChildren<HTMLAttributes<HTMLDivElement>> & {
  accentClassName?: string;
};

export function InteractiveCard({
  children,
  className = "",
  accentClassName = "bg-red-700",
  ...props
}: Props) {
  return (
    <Card
      className={`relative overflow-hidden transition-[transform,border-color,box-shadow,background-color] duration-200 ease-out hover:-translate-y-0.5 hover:border-red-200 hover:shadow-md active:translate-y-0 active:shadow-sm ${className}`}
      {...props}
    >
      <span
        aria-hidden="true"
        className={`absolute inset-y-0 left-0 w-1 ${accentClassName}`}
      />
      {children}
    </Card>
  );
}
