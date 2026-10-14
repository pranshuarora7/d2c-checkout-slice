type Props = {
  name: string;
  quantity: number;
  max: number;
  disabled?: boolean;
  onChange: (quantity: number) => void;
};

export function QuantityControl({ name, quantity, max, disabled, onChange }: Props) {
  return (
    <div className="quantity-control" role="group" aria-label={'Quantity for ' + name}>
      <button
        type="button"
        aria-label={'Decrease ' + name + ' quantity'}
        disabled={disabled}
        onClick={() => onChange(quantity - 1)}
      >
        −
      </button>
      <span aria-live="polite" aria-label={name + ' quantity in cart'}>
        {quantity}
      </span>
      <button
        type="button"
        aria-label={'Increase ' + name + ' quantity'}
        disabled={disabled || quantity >= max}
        onClick={() => onChange(quantity + 1)}
      >
        +
      </button>
    </div>
  );
}
