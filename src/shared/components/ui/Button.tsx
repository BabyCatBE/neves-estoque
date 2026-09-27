import type {
  ButtonHTMLAttributes,
  PropsWithChildren,
  ReactNode
} from "react";

type ButtonVariant = "primary" | "secondary" | "danger" | "ghost";
type ButtonSize = "sm" | "md";

type Props = PropsWithChildren<
  ButtonHTMLAttributes<HTMLButtonElement> & {
    variant?: ButtonVariant;
    size?: ButtonSize;
    isLoading?: boolean;
    loadingLabel?: ReactNode;
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
  isLoading = false,
  loadingLabel,
  disabled,
  ...props
}: Props) {
  return (
    <button
      type={type}
      disabled={disabled || isLoading}
      aria-busy={isLoading || undefined}
      className={`inline-flex items-center justify-center gap-2 rounded-xl font-semibold transition-[background-color,border-color,color,box-shadow,transform] duration-150 active:scale-[0.985] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-600 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:active:scale-100 ${variantClasses[variant]} ${sizeClasses[size]} ${className}`}
      {...props}
    >
      {isLoading ? (
        <span
          aria-hidden="true"
          className="h-4 w-4 shrink-0 animate-spin rounded-full border-2 border-current border-r-transparent opacity-70"
        />
      ) : null}
      {isLoading && loadingLabel ? loadingLabel : children}
    </button>
  );
}
