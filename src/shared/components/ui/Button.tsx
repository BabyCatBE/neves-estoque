import type { ButtonHTMLAttributes, PropsWithChildren } from "react";

type ButtonVariant = "primary" | "secondary" | "danger" | "ghost";
type ButtonSize = "sm" | "md";

type Props = PropsWithChildren<
  ButtonHTMLAttributes<HTMLButtonElement> & {
    variant?: ButtonVariant;
    size?: ButtonSize;
  }
>;

const variantClasses: Record<ButtonVariant, string> = {
  primary: "bg-red-700 text-white hover:bg-red-800 disabled:bg-zinc-200 disabled:text-zinc-500",
  secondary:
    "border border-red-200 bg-white text-red-700 hover:bg-red-50 disabled:border-zinc-200 disabled:text-zinc-400",
  danger: "bg-red-700 text-white hover:bg-red-800 disabled:bg-zinc-200 disabled:text-zinc-500",
  ghost: "bg-transparent text-zinc-700 hover:bg-zinc-100 disabled:text-zinc-400"
};

const sizeClasses: Record<ButtonSize, string> = {
  sm: "min-h-9 px-3 py-2 text-xs",
  md: "min-h-11 px-4 py-2.5 text-sm"
};

export function Button({
  children,
  className = "",
  variant = "primary",
  size = "md",
  type = "button",
  ...props
}: Props) {
  return (
    <button
      type={type}
      className={`inline-flex items-center justify-center rounded-xl font-semibold transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-600 focus-visible:ring-offset-2 disabled:cursor-not-allowed ${variantClasses[variant]} ${sizeClasses[size]} ${className}`}
      {...props}
    >
      {children}
    </button>
  );
}
