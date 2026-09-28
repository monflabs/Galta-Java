function up(s) {
  return s.toString().toUpperCase();
}

function add(s,a) {
  return s+a;
}


assertEquals("ABC", "aBc" |> up);
assertEquals("abc","aBc" |> (s) => s.toLowerCase() );
assertEquals("aBcX","aBc" |> add("X"));

assertEquals("aBcXY","aBc" |> add("X") |> add("Y"));


assertEquals("ABCX","aBc" |> up |> add("X"));
assertEquals("abcx","aBc" |> add("x") |> (s) => s.toLowerCase() );
assertEquals("ABCX","aBc" |> (s) => s.toLowerCase() |> add("X") |> up );
