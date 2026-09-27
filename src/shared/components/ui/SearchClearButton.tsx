type Props = {
  onClear: () => void;
  placement?: "text-field" | "input";
};

export function SearchClearButton({
  onClear,
  placement = "text-field"
}: Props) {
  const position =
    placement === "input"
      ? "right-2 top-1/2 -translate-y-1/2"
      : "bottom-1.5 right-2";

  return (
    <button
      type="button"
      aria-label="Limpar pesquisa"
      title="Limpar pesquisa"
      onClick={onClear}
      className={`absolute ${position} flex h-8 w-8 items-center justify-center rounded-lg text-red-700 transition-[transform,background-color,color] duration-150 hover:bg-red-50 hover:text-red-800 active:scale-90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-600`}
    >
      <svg aria-hidden="true" viewBox="0 0 24 24" className="h-4 w-4" fill="none">
        <path
          d="m7 7 10 10M17 7 7 17"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
        />
      </svg>
    </button>
  );
}
