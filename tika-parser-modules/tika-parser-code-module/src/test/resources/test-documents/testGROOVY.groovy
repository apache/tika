//hello.groovy
println "hello, world"
for (arg in this.args ) {
  println "Argument:" + arg;
}
// this is a comment
/* a block comment, commenting out an alternative to above:
this.args.each{ arg -> println "hello, ${arg}"}
*/