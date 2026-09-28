type strType = string
type strOrNum = string | number
type tsType = 'TypeScript'

let ts: strType = 'TypeScript'
let ts2 = ts as strType // less specific
let typeScript = ts as tsType // more specific

let myStr = <strType>'Riyad'
let myStr2 = <number | string>'Riyad'

const addOrConcat = (a: number, b: number, c: 'add' | 'concat'): number | string => {
  if (c === 'add') return a + b
  return '' + a + b
}
let myVal: string = addOrConcat(2, 2, 'concat') as string // To prevent getting error

// The DOM
const img = document.querySelector('img')!
const myImg = document.getElementById('#img') as HTMLImageElement
const nextImg = <HTMLImageElement>document.getElementById('#img')
img.src
myImg.src

const year = document.getElementById("year") as HTMLSpanElement
const thisYear: string = new Date().getFullYear().toString()
year.setAttribute("datetime", thisYear)
year.textContent = thisYear
