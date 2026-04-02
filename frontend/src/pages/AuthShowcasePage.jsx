export default function AuthShowcase({ eyebrow, title, description, badges = [], cards = [], note = '', variant = 'default' }) {
  return (
    <section className={`authShowcase ${variant === 'compact' ? 'authShowcaseCompact' : ''}`}>
      <div className="authShowcaseInner">
        <div className="authTag">{eyebrow}</div>
        <h1 className="authTitle">{title}</h1>
        <p className="authCopy">{description}</p>

        <div className="authPillRow">
          {badges.map((badge) => (
            <span key={badge} className="authPill">{badge}</span>
          ))}
        </div>

        <div className="authFeatureGrid">
          {cards.map((card, index) => (
            <article key={`${card.title}-${index}`} className={`authFeatureCard authFeatureCard${(index % 3) + 1}`}>
              <div className="authFeatureValue">{card.value}</div>
              <div className="authFeatureTitle">{card.title}</div>
              <p className="authFeatureText">{card.text}</p>
            </article>
          ))}
        </div>

        {note ? <div className="authNote">{note}</div> : null}
      </div>
    </section>
  )
}
