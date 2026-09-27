import { useId, type InputHTMLAttributes } from "react";

type Props = InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  error?: string | null;
};

export function TextField({ label, error, className = "", id, ...props }: Props) {
  const generatedId = useId();
  const inputId = id ?? props.name ?? generatedId;
  const errorId = `${inputId}-error`;
  const describedBy = [props["aria-describedby"], error ? errorId : null]
    .filter(Boolean)
    .join(" ") || undefined;

  return (
    <label className="block" htmlFor={inputId}>
      <span className="text-sm font-medium text-zinc-800">{label}</span>
      <input
        id={inputId}
        aria-invalid={error ? true : props["aria-invalid"]}
        aria-describedby={describedBy}
        className={`mt-2 min-h-11 w-full scroll-mt-24 rounded-xl border bg-white px-3 py-2 text-sm text-zinc-900 outline-none transition-[border-color,box-shadow,background-color] duration-150 placeholder:text-zinc-400 focus:border-red-500 focus:ring-2 focus:ring-red-100 disabled:cursor-not-allowed disabled:bg-zinc-100 disabled:text-zinc-500 ${
          error ? "border-red-400" : "border-zinc-300"
        } ${className}`}
        {...props}
      />
      {error ? (
        <span id={errorId} role="alert" className="mt-1.5 block text-xs font-medium text-red-700">
          {error}
        </span>
      ) : null}
    </label>
  );
}
