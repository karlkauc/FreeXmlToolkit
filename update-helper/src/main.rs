mod error;

fn main() {
    println!("fxt-update-helper {} (Rust)", env!("CARGO_PKG_VERSION"));
    std::process::exit(error::EXIT_SUCCESS);
}
