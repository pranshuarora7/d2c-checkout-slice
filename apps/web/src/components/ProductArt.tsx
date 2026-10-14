export function ProductArt({ art, className = '' }: { art: string; className?: string }) {
  return (
    <div className={'product-art art-' + art + ' ' + className}>
      <img src={'/images/' + art + '.svg'} alt="" />
    </div>
  );
}
