# Numbers

The containers accept any `Number`, and the typed getters convert between them. The
parser gives the smallest fitting type: `Integer`, `Long`, `BigInteger`, `Double`, or a
`BigDecimal` for a decimal a `double` can't hold exactly.
